(ns com.ben-allred.espresso.core
  (:require
    [clojure.string :as string]
    [com.ben-allred.vow.core :as v :include-macros true]
    http))

(defn ^:private ->request [req res]
  (let [url (.-url req)
        [path query-string] (string/split url #"\?")]
    (cond-> {:headers     (js->clj (.-headers req))
             :method      (keyword (string/lower-case (.-method req)))
             :url         url
             :path        path
             :js/request  req
             :js/response res}
      query-string (assoc :query-string query-string))))

(defn ^:private ->response [res]
  (fn [response]
    (.writeHead res
                (:status response 200)
                (some-> response :headers clj->js))
    (let [body (:body response)
          encoding (:encoding response "utf8")]
      (cond
        (nil? body) (.end res)
        (js/Buffer.isBuffer body) (.end res body encoding)
        :else (.end res (str body) "utf8")))))

(defn wrap-handler
  "Converts an espresso handler fn into a callback suitable for a NodeJS server"
  [handler]
  (fn [req res]
    (-> (v/vow (handler (->request req res)))
        (v/catch (fn [err] {:status 500 :body err}))
        (v/then (->response res)))))

(def ^{:arglists '([handler])} create-server
  "Takes an espresso handler and makes a NodeJS http sever.

  (def server (create-server my-handler))
  (.listen server 8080 (fn [] \"the server is listening\"))"
  (comp http/createServer wrap-handler))

(defn combine
  "A function for composing two or more handlers. Each handler will be called from left to right until
  one returns a result other than `nil` (or all handlers have been called)."
  ([]
   (constantly (v/resolve)))
  ([handler]
   handler)
  ([handler-1 & handlers]
   (reduce (fn [handler f]
             (fn [request]
               (-> (handler request)
                   (v/then (fn [response]
                             (if (nil? response)
                               (f request)
                               response))))))
           handler-1
           handlers)))
