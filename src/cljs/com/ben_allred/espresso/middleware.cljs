(ns com.ben-allred.espresso.middleware
  (:require
    [com.ben-allred.vow.core :as v :include-macros true])
  (:import
    (goog.string StringBuffer)))

(defn ^:private parse-fn [parse-fns content-type]
  (or (->> (when content-type
             parse-fns)
           (keep (fn [[re parse-fn]]
                   (when (and (regexp? re)
                              (re-matches re content-type))
                     parse-fn)))
           first)
      (:default parse-fns)
      identity))

(defn with-body
  "A middleware for reading the body of the request into a string"
  ([handler]
   (with-body handler nil))
  ([handler opts]
   (let [encoding (:encoding opts "utf8")]
     (fn [request]
       (v/then-> (v/create (fn [resolve reject]
                             (let [buffer (StringBuffer.)]
                               (doto (:js/request request)
                                 (.setEncoding encoding)
                                 (.on "data" (fn [chunk]
                                               (.append buffer chunk)))
                                 (.on "end" (fn []
                                              (v/then (v/promise (str buffer))
                                                      resolve
                                                      reject)))
                                 (.on "error" reject)))))
                 (->> (assoc request :body))
                 handler)))))

(defn with-content-type
  "A middleware for deserializing/serializing the request/response body based on headers

  (with-content-type my-handler {:deserializers {#\"^application/json.+\" #(js->clj (js/JSON.parse %) :keywordize-keys true)
                                                 :default               edn/read-string}
                                 :serializers   {#\"^application/json.+\" #(js/JSON.stringify (clj->js %))
                                                 :default               pr-str}})"
  [handler opts]
  (with-body (fn [{:keys [headers] :as request}]
               (let [body (not-empty (:body request))
                     {accept "accept" content-type "content-type"} headers
                     deserializer (parse-fn (:deserializers opts) content-type)]
                 (-> (cond-> request
                       (not body) (dissoc :body)
                       body (update :body deserializer))
                     handler
                     (v/then (fn [{:keys [headers body] :as response}]
                               (let [serializer (parse-fn (:serializers opts) accept)
                                     content-type' (get headers "content-type")
                                     serializer' (parse-fn (:serializers opts) content-type')]
                                 (cond
                                   (empty? body) (dissoc response :body)
                                   (or (not accept) content-type') (update response :body serializer')
                                   accept (-> response
                                              (update :body serializer)
                                              (update :headers assoc "content-type" accept))
                                   :else response)))))))
             opts))
