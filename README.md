# espresso cljs

A wrapper around NodeJS's [http module](https://nodejs.org/api/http.html) for building composable web server
applications in Clojurescript.

## Getting Started

A `handler` is just a clojurescript function that takes a request map and returns an asynchronous response map.
Asynchronous values are expressed using [com.ben-allred/vow](https://www.github.com/skuttleman/vow) - a
Clojure/script interface that mimics Javascript promises.

Here is a simple example.

```clojure
(require '[com.ben-allred.espresso.core :as es])
(require '[com.ben-allred.vow.core :as v])

(def my-handler [request]
  (v/resolve {:status  200
              :headers {"content-type" "application/json"}
              :body    "{\"some\":\"json\"}"}))

(def server (es/create-server my-handler))
;; same as (http/createServer (es/wrap-handler my-handler))

(.listen server 3000 (fn [] (js/console.log "The server is listening on PORT 3000")))
```

```bash
$ curl http://localhost:3000
# => {"some":"json"}
```

### Middleware

Like `ring` for clojure, middleware is built by simply passing your handler to another function that returns a new handler.

```clojure
(require '[com.ben-allred.espresso.core :as es])
(require '[com.ben-allred.vow.core :as v])

(def my-handler [request]
  (v/resolve {:status  200
              :headers {"content-type" "application/json"}
              :body    "{\"some\":\"json\"}"}))

(defn my-middleware [handler]
  (fn [request]
    (js/console.log "Request received at:" (js/Date.))
    (v/peek (handler request)
            (fn [_]
              (js/console.log "Response sent at:" (js/Date.))))))

(def my-error-handler [handler]
  (fn [request]
    (v/catch (handler request)
             (fn [err]
               (js/console.error err)
               {:status 500 :body "An unknown error occurred. Sux 4 u."}))))

(def server (-> my-handler
                my-middleware
                my-error-handler
                es/create-server))
(.listen server 3000)
```

### Request body

In NodeJS, the `request` object is a readable stream of the body. Typically you'll want to process this in one of two
ways. 1) Read the body into memory and parse it into something usable, or 2) pipe it to some other target. For cases
when you want to parse it in memory, a convenience middleware is provided that reads the stream into a string.

```clojure
(require '[com.ben-allred.espresso.core :as es])
(require '[com.ben-allred.espresso.middleware :as esmw])
(require '[com.ben-allred.vow.core :as v])

(defn my-handler [request]
  (v/resolve {:status 200
              :body   (str "echo:" (:body request))}))

(def server (-> my-handler
                esmw/with-body
                es/create-server))
(.listen server 3000)
```

```bash
$ curl -XPOST http://localhost:3000 --data 'this is the body'
# => echo:this is the body
```

If you want to pipe the body to another target or have access to NodeJS's lower level API for some reason, the `Request`
and `Response` objects exist on the `request` map as `:js/request` and `:js/response` respectively.

#### Parsing the body

Here is an example of deserializing/serializing the request/response body in middleware.

```clojure
(require '[com.ben-allred.espresso.core :as es])
(require '[com.ben-allred.espresso.middleware :as esmw])
(require '[com.ben-allred.vow.core :as v])
(require '[cljs.tools.reader.edn :as edn])

(defn my-handler [request]
  (v/resolve {:status 200
              :body   {:request (:body request)}}))

(def server (-> my-handler
                (esmw/with-content-type {:deserializers {#"^application/json.*" #(js->clj (js/JSON.parse %) :keywordize-keys true)
                                                         #"^application/edn.*"  edn/read-string}
                                         :serializers   {#"^application/json.*" #(js/JSON.stringify (clj->js %))
                                                         #"^application/edn.*"  pr-str}})
                es/create-server))
(.listen server 3000)
```

```bash
$ curl -XPOST -H 'Content-Type: application/json' -H 'Accept: application/json' http://localhost:3000 --data '{"some":"json"}'
# => {"request":{"some":"json"}}
$ curl -XPOST -H 'Content-Type: application/edn' -H 'Accept: application/edn' http://localhost:3000 --data '{:some :edn}'
# => {:request {:some :edn}}
```

## Intermediate topics

### Composing handlers

If you're handler resolves to `nil` - i.e. `(v/resolve nil)` - it can be composed with other handlers. Handlers will be
called from left to right until a rejection is returned, or a value other than `nil` is resolved.

```clojure
(require '[com.ben-allred.espresso.core :as es])
(require '[com.ben-allred.vow.core :as v])

(def foo (es/combine (constantly (v/resolve))
                     (constantly (v/resolve :foo))
                     (constantly (v/resolve :won't-happen))))
(v/peek (foo "request") println)
;; [:success :foo]

(def bar (es/combine (constantly (v/reject))
                     (constantly (v/resolve :won't-happen))))
(v/peek (bar "request") println)
;; [:error nil]
```

Here's an example of using it as a handler.

```clojure
(require '[com.ben-allred.espresso.core :as es])
(require '[com.ben-allred.vow.core :as v])

(defn handler-1 [request]
  (v/resolve (when (= (:method request) :get)
               {:status 200
                :body   "get"})))

(defn handler-2 [request]
  (v/resolve (when (= (:method request) :post)
               {:status 200
                :body   "post"})))

(def my-default-handler
  (constantly (v/resolve {:status 404
                          :body   "not found"})))

(def server (es/create-server (es/combine my-handler-1 my-handler-2 my-default-handler)))
(.listen server 3000)
```

```bash
$ curl http://localhost:3000
# => get
$ curl -XPOST http://localhost:3000
# => post
$ curl -XPUT -v http://localhost:3000
# => not found
```

### Routing

The `espresso` library does not handle routing. Feel free to use your favorite Clojurescript-friendly routing library.

#### An example using `bidi`

```clojure
(require '[com.ben-allred.espresso.core :as es])
(require '[com.ben-allred.vow.core :as v])
(require '[bidi.bidi :as bidi])

(defmulti my-handler :bidi/route)

(defmethod my-handler :foo/* [_]
  (v/resolve {:status 200
              :body   "foo"}))

(defmethod my-handler :bar/get [{:bidi/keys [route-params]}]
  (v/resolve {:status 200
              :body   (str [:bar/get route-params])}))

(defmethod my-handler :bar/post [{:bidi/keys [route-params]}]
  (v/resolve {:status 201
              :body   (str [:bar/post route-params])}))

(defmethod my-handler :default [_]
  (v/resolve))

(defmulti my-admin-handler :bidi/route)

(defmethod my-admin-handler :secrets/get [_]
  (v/then (look-up-secrets)
          (partial assoc {:status 200} :body)))

(defmethod my-admin-handler :default [_]
  (v/resolve))

(def my-not-found-handler
  (constantly (v/resolve {:status 404})))

(defn my-auth-middleware [handler]
  (fn [request]
    (-> (authenticated? request)
        (v/then handler (constantly {:status 401})))))

(defn with-routing [handler routes]
  (fn [request]
    (let [{route :handler :keys [route-params]} (bidi/match-route routes
                                                                  (:path request)
                                                                  :request-method
                                                                  (:method request))]
      (handler (cond-> request
                 route (assoc :bidi/route route :bidi/route-params route-params))))))

(def server
  (es/create-server (es/combine (with-routing my-handler
                                              ["" {"/foo"            :foo/*
                                                   ["/bar/" :bar-id] {:get  :bar/get
                                                                      :post :bar/post}}])
                                (-> my-auth-handler
                                    my-auth-middleware
                                    (with-routing ["" {"/admin/secrets" :secrets/get}]))
                                my-not-found-handler)))
(.listen server 3000)
```

```bash
$ curl http://locahost:3000/foo
# => foo
$ curl http://localhost:3000/bar/123
# => [:bar/get {:bar-id "123"}]
$ curl -XPOST http://localhost:3000/bar/baz
# => [:bar/post {:bar-id "baz"}]
$ curl -H 'Authorization: {ADMIN_TOKEN}' http://localhost:3000/admin/secrets
# => some juicy secrets
$ curl -H 'Authorization: {NON_ADMIN_TOKEN}' -v http://localhost:3000/admin/secrets
# ...
# < HTTP/1.1 401 Unauthorized
# ...
$ curl -v http://localhost:3000
# ...
# < HTTP/1.1 404 Not Found
# ...
```
