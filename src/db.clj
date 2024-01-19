(ns db
  (:require [datalevin.core :as d]))

(defonce conn (d/get-conn (System/getenv "GARDEN_STORAGE")))

(defn listens []
  (let [db (d/db conn)
        res (d/q '[:find [?l ...]
                   :where
                   [?l :listened-at ?l-at]]
                 db)]
    (map (comp d/touch (partial d/entity db)) res)))

(defn listens-in-range [start end]
  (let [db (d/db conn)
        res (d/q '[:find [?l ...]
                   :in $ ?start ?end
                   :where
                   [?l :listened-at ?l-at]
                   [(>= ?l-at ?start)]
                   [(>= ?end ?l-at)]]
                 db
                 start
                 end)]
    (map (comp d/touch (partial d/entity db)) res)))

(defn track-counts []
  (->> (d/q '[:find ?recording-mbid (count ?l)
              :keys name listens
              :where [?l :recording-mbid ?recording-mbid]]
            (d/db conn))
       (sort-by :listens)
       reverse))

(defn album-counts []
  (->> (d/q '[:find ?release-mbid (count ?l) .
              :keys name listens
              :where [?l :release-mbid ?release-mbid]]
            (d/db conn))
       (sort-by :listens)
       reverse))

(defn artist-counts []
  (->> (d/q '[:find ?artist-name (count ?l)
              :keys name listens
              :where [?l :artist-name ?artist-name]]
            (d/db conn))
       (sort-by :listens)
       reverse))

(defn transform [{:keys [listened-at track-metadata]}]
  (let [now (.getEpochSecond (java.time.Instant/now))
        {:keys [additional-info]} track-metadata
        additional-info-keys [:recording-mbid :release-mbid :artist-mbids]
        extra-additional-info (apply dissoc additional-info additional-info-keys)]
    (cond-> (merge {:listened-at (or listened-at now)}
                   (select-keys track-metadata [:artist-name :release-name :track-name])
                   (select-keys additional-info additional-info-keys))
      (seq extra-additional-info) (assoc :additional-info extra-additional-info)
      (nil? listened-at) (assoc-in [:additional-info :guessed-listened-at] true))))

(defn record-listens [listens]
  (d/transact! conn (mapv transform listens)))
