(ns com.ben-allred.espresso.middleware
  (:require
    [com.ben-allred.vow.core :as v :include-macros true])
  (:import
    (goog.string StringBuffer)))

(defn with-body
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
