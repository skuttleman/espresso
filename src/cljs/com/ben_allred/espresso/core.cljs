(ns com.ben-allred.espresso.core
  (:refer-clojure :exclude [comp])
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

(defn wrap-handler [handler]
  (fn [req res]
    (-> (v/promise (handler (->request req res)))
        (v/catch (fn [err] {:status 500 :body err}))
        (v/then (->response res)))))

(def ^{:arglists '([handler])} create-server
  (clojure.core/comp http/createServer wrap-handler))

(defn comp
  ([]
   (constantly (v/resolve)))
  ([handler]
   handler)
  ([handler-1 & handlers]
   (reduce (fn [handler f]
             (fn [request]
               (-> (f request)
                   (v/then (fn [response]
                             (if (nil? response)
                               (handler request)
                               response))))))
           handler-1
           handlers)))
