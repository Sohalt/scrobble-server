(ns db
  (:require [datalevin.core :as d])
  (:import (java.time Instant)))

(defonce conn (d/get-conn (or (System/getenv "GARDEN_STORAGE") "data")))

(defprotocol Timestamp
  (->timestamp [this]))

(extend-protocol Timestamp
  java.util.Date
  (->timestamp [this] (.getEpochSecond (.toInstant this)))
  java.time.LocalDate
  (->timestamp [this] (.getEpochSecond (.toInstant this)))
  java.time.Instant
  (->timestamp [this] (.getEpochSecond this))
  java.lang.String
  (->timestamp [this] (.getEpochSecond (Instant/parse this)))
  java.lang.Long
  (->timestamp [this] this))

(defn listens
  ([] (listens nil))
  ([start] (listens start nil))
  ([start end]
   (let [start (some-> start ->timestamp)
         end (some-> end ->timestamp)
         db (d/db conn)
         res (d/q (cond-> `[:find [?l ...]
                            :where
                            [?l :listened-at ?l-at]]
                    start (conj `[(>= ?l-at ~start)])
                    end (conj `[(>= ~end ?l-at)]))
                  db)]
     (map (comp d/touch (partial d/entity db)) res))))

(defn track-counts
  ([] (track-counts nil))
  ([start] (track-counts start nil))
  ([start end]
   (let [start (some-> start ->timestamp)
         end (some-> end ->timestamp)
         db (d/db conn)]
     (->> (d/q (cond-> `[:find ?recording-mbid (count ?l)
                         :where
                         [?l :recording-mbid ?recording-mbid]
                         [?l :listened-at ?l-at]]
                 start (conj `[(>= ?l-at ~start)])
                 end (conj `[(>= ~end ?l-at)]))
               db)
          (map (fn [[recording-mbid count]]
                 (let [listen-id (d/q '[:find ?l .
                                        :in $ ?recording-mbid
                                        :where [?l :recording-mbid ?recording-mbid]]
                                      db
                                      recording-mbid)]
                   {:listen (d/entity db listen-id)
                    :count count})))
          (sort-by :count)
          reverse))))

(defn album-counts
  ([] (album-counts nil))
  ([start] (album-counts start nil))
  ([start end] (let [start (some-> start ->timestamp)
                     end (some-> end ->timestamp)
                     db (d/db conn)
                     counts (d/q (cond-> `[:find ?release-mbid (count ?l)
                                           :where
                                           [?l :release-mbid ?release-mbid]
                                           [?l :listened-at ?l-at]]
                                   start (conj `[(>= ?l-at ~start)])
                                   end (conj `[(>= ~end ?l-at)]))
                                 db)]
                 ;;HACK get the first listen for an album, because the rendering expects it that way
                 (->> counts
                      (map (fn [[release-mbid count]]
                             (let [listen-id (d/q '[:find ?l .
                                                    :in $ ?release-mbid
                                                    :where [?l :release-mbid ?release-mbid]]
                                                  db
                                                  release-mbid)]
                               {:listen (d/entity db listen-id)
                                :count count})))
                      (sort-by :count)
                      reverse))))

(defn artist-counts []
  (->> (d/q '[:find ?artist-name (count ?l)
              :where [?l :artist-name ?artist-name]]
            (d/db conn))
       (map (fn [[artist-name count]] {:artist-name artist-name
                                       :count count}))
       (sort-by :count)
       reverse))

(defn transform [{:keys [listened-at track-metadata]}]
  ;;TODO store LocalDateTime, otherwise analysis like hour of day is meaningless
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
