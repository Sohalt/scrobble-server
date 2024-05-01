(ns main
  (:require [db]
            [stats-page]
            [org.httpkit.server :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn authorized? [req]
  (= (System/getenv "AUTH_TOKEN")
     (second (str/split (get-in req [:headers "authorization"]) #" "))))

(defn json-response [payload]
  {:status 200
   :headers {"content-type" "application/json"}
   :body (json/encode payload)})

(defonce !listens (atom []))

(defn submit-listens [{:as listen :keys [payload listen-type]}]
  (swap! !listens conj listen)
  (case listen-type
    "playing_now" (json-response {:status "ok"})
    "single" (do (db/record-listens payload) (json-response {:status "ok"}))
    "import" (do (db/record-listens payload) (json-response {:status "ok"}))))

(defn snake->kebab [s]
  (str/replace s "_" "-"))

(defn app [req]
  (case (:uri req)
    "/" {:status 200
         :headers {"content-type" "text/html"}
         :body (stats-page/page)}
    "/1/validate-token" (if-not (authorized? req)
                          {:status 403}
                          (json-response {:valid true
                                          :user_name "_"}))
    "/1/submit-listens" (if-not (authorized? req)
                          {:status 403}
                          (submit-listens (json/parse-stream (io/reader (:body req)) (comp keyword snake->kebab))))
    {:status 404}))

(defonce !server (atom nil))

(defn start! [opts]
  (if (nil? @!server)
    (let [s (http/run-server #'app (merge {:port 7778} opts {:legacy-return-value? false}))]
      (println (format "Started server on port %s" (http/server-port s)))
      (reset! !server s))
    (println "Already running")))

(defn stop! []
  (when-let [s @!server]
    (http/server-stop! s)
    (reset! !server nil)))

(comment
  (start! {})
  (stop!))

(defn -main [& args]
  (start! {}))
