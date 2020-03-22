# espresso cljs

A wrapper around NodeJS's [http module](https://nodejs.org/api/http.html) for building composable web server
applications in Clojurescript.

## Getting Started

A `handler` is just a clojurescript function that takes a request map and returns an asynchronous response map.
Asynchronous values are expressed using [com.ben-allred/vow](https://www.github.com/skuttleman/vow) - a
Clojure/script interface that mimics Javascript promises.

Here is a simple example.

```clojure
(require '[com.ben-allred.espresso.core :as espresso])
(require '[com.ben-allred.vow.core :as v])

(def my-handler [request]
  (v/resolve {:status  200
              :headers {"content-type" "application/json"}
              :body    "{\"some\":\"json\"}"}))

(def server (espresso/create-server my-handler))
;; same as (http/createServer (espresso/wrap-handler my-handler))

(.listen server 3000 (fn [] (js/console.log "The server is listening on PORT 3000")))
```

```bash
$ curl http://localhost:3000
# => {"some":"json"}
```

### Middleware

Like `ring` for clojure, middleware is built by simply passing your handler to another function that returns a new handler.

```clojure
(require '[com.ben-allred.espresso.core :as espresso])
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
                espresso/create-server))
(.listen server 3000)
```

### Request body

In NodeJS, the `request` object is a readable stream of the body. Typically you'll want to process this in one of two
ways. 1) Read the body into memory and parse it into something usable, or 2) pipe it to some other target. For cases
when you want to parse it in memory, a convenience middleware is provided that reads the stream into a string.

```clojure
(require '[com.ben-allred.espresso.core :as espresso])
(require '[com.ben-allred.espresso.middleware :as espressomw])
(require '[com.ben-allred.vow.core :as v])

(defn my-handler [request]
  (v/resolve {:status 200
              :body   (str "echo:" (:body request))}))

(def server (-> my-handler
                espressomw/with-body
                espresso/create-server))
(.listen server 3000)
```

```bash
$ curl -XPOST http://localhost:3000 --data 'this is the body'
# => echo:this is the body
```

If you want to pipe the body, of have access to NodeJS's lower level API for some reason, the `Request` and `Response`
objects exist on the `request` map as `:js/request` and `:js/response` respectively.

#### Parsing the body

Here is an example of parsing JSON.

```clojure
(require '[com.ben-allred.espresso.core :as espresso])
(require '[com.ben-allred.espresso.middleware :as espressomw])
(require '[com.ben-allred.vow.core :as v])

(defn my-handler [request]
  (v/resolve {:status 200
              :body   (str "echo:" (:body request))}))

(defn as-json [handler]
  (fn [request]
    (-> request
      (update :body #(js->clj (js/JSON.parse %) :keywordize-keys true))
      handler)))

;; NOTE: middleware functions process the request from right to left (bottom to top),
;; and process the response from left to right (top to bottom).
(def server (-> my-handler
                as-json 
                espressomw/with-body
                espresso/create-server))
(.listen server 3000)
```

```bash
$ curl -XPOST http://localhost:3000 --data '{"some":"json"}'
# => echo:{:some "json"}
```

### Routing

The `espresso` library does not handle routing. Feel free to use your favorite Clojurescript-friendly routing library.

#### An example using `bidi`

```clojure
(require '[com.ben-allred.espresso.core :as espresso])
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
  (v/resolve {:status 404}))

(defn with-routing [handler routes]
  (fn [request]
    (let [{route :handler :keys [route-params]} (bid/match-route routes
                                                                 (:path request)
                                                                 :request-method
                                                                 (:method request))]
      (handler (cond-> request
                 route (assoc :bidi/route route :bid/route-params route-params))))))

(def server (-> my-handler
                (with-routing ["" {"/foo" :foo/*
                                   ["/bar/" :bar-id] {:get  :bar/get
                                                      :post :bar/post}}])
                espresso/create-server))
(.listen server 3000)
```

```bash
$ curl http://locahost:3000/foo
# => foo
$ curl http://localhost:3000/bar/123
# => [:bar/get {:bar-id "123"}]
$ curl -XPOST http://localhost:3000/bar/baz
# => [:bar/post {:bar-id "baz"}]
$ curl -v http://localhost:3000
# ...
# < HTTP/1.1 404 Not Found
# ...
```
