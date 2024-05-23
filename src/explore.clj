(ns explore
  (:require [db]
            [viz]
            [time]
            [nextjournal.clerk :as clerk])
  (:import (java.time ZoneId Instant)))

;; get all listens

(def listens (db/listens))

(clerk/table listens)

(->> listens
     (mapcat keys)
     frequencies)

;; have a look at the first one

(def listen (first listens))

(defn retry
  ([f] (retry f {}))
  ([f {:keys [times delay] :or {times 5 delay 100}}]
   (fn [& args]
     (loop [tries-left times]
       (let [res (try
                   (apply f args)
                   (catch Exception e
                     (if (> tries-left 1)
                       ::retry
                       (throw e))))]
         (if (= ::retry res)
           (do (Thread/sleep delay)
               (recur (dec tries-left)))
           res))))))


(viz/lookup "release" (:release-mbid listen))

(viz/lookup "recording" (:recording-mbid listen))

(viz/track-name listen)

(viz/album-name listen)

(viz/artist-name listen)

^{::clerk/viewer clerk/html}
(viz/cover-art-img listen)

(viz/listen-info listen)

;; ## 10 random songs of all time

^{::clerk/viewer clerk/html}
(viz/song-grid (take 10 (shuffle listens)))

;; ## 10 random songs of this week

^{::clerk/viewer clerk/html}
(viz/song-grid (take 10 (shuffle (db/listens (time/start-of-week)))))

;; ## Top 10 songs of all time

^{::clerk/viewer clerk/html}
(->>
 (db/track-counts)
 (take 10)
 (map :listen)
 (viz/song-grid))

;; ## Top 10 songs of the week

^{::clerk/viewer clerk/html}
(->>
 (db/track-counts (time/start-of-week))
 (take 10)
 (map :listen)
 (viz/song-list))

;; ## Top 10 songs of the month

^{::clerk/viewer clerk/html}
(->>
 (db/track-counts (time/start-of-month))
 (take 10)
 (map :listen)
 (viz/song-list))

;; ## Top 10 songs of the year

^{::clerk/viewer clerk/html}
(->>
 (db/track-counts (time/start-of-year))
 (take 10)
 (map :listen)
 (viz/song-list))

;; ## Top 10 albums

^{::clerk/viewer clerk/html}
(->>
 (db/album-counts)
 (take 10)
 (map :listen)
 (map #(dissoc % :track-name :recording-mbid))
 (viz/song-grid))

;; ## Top 10 artists

^{::clerk/viewer clerk/html}
(->>
 (db/artist-counts)
 (take 10)
 (map :artist-name)
 (viz/artist-list))


;; ## Listen Times
^{::clerk/viewer clerk/vl}
{:data {:values (->> listens
                     (group-by (fn [l] (-> l :listened-at Instant/ofEpochSecond (.atZone (ZoneId/of "Europe/Berlin")) .getHour)))
                     (map (fn [[h l]] {:hour h :count (count l)})))}
 :mark :bar
 :encoding {:x {:field :hour
                :type :nominal}
            :y {:field :count
                :type :quantitative}}}

;; ## Listens by Year

^{::clerk/viewer clerk/vl}
{:data {:values (->> listens
                     (group-by (fn [l] (some->> l viz/year)))
                     (map (fn [[y l]] {:year y :count (count l)}))
                     (sort-by :year))}
 :mark :bar
 :encoding {:x {:field :year
                :type :nominal}
            :y {:field :count
                :type :quantitative}}}

;; ## Listens by Genre

(defn genre [listen]
  ;;TODO
  "unknown")

^{::clerk/viewer clerk/vl}
{:data {:values (->> listens
                     (group-by genre)
                     (map (fn [[g l]] {:genre g :count (count l)})))}
 :mark :arc
 :encoding {:color {:field :genre
                    :type :nominal}
            :theta {:field :count
                    :type :quantitative}}}
