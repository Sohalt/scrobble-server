(ns stats-page
  (:require [db]
            [viz]
            [time]
            [huff.core :as h]))

(def tw-config
  "tailwind.config = { theme: {fontFamily: { sans: [\"Fira Sans\", \"-apple-system\", \"BlinkMacSystemFont\", \"sans-serif\"], serif: [\"PT Serif\", \"serif\"], mono: [\"Fira Mono\", \"monospace\"] } } }")

(defn page []
  (h/page
   {:allow-raw true}
   [:<>
    [:head
     [:meta {:charset "UTF-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:script {:type "text/javascript" :src "https://cdn.tailwindcss.com?plugins=typography"}]
     [:script [:hiccup/raw-html tw-config]]]
    [:body
     [:h1.text-5xl "Listening stats"]
     [:h2.p-5.text-3xl "Top 10 songs of the week"]
     (->> (db/track-counts (time/start-of-week))
          (take 10)
          (map :listen)
          (viz/song-grid))
     [:h2.p-5.text-3xl "Top 10 songs of the month"]
     (->> (db/track-counts (time/start-of-month))
          (take 10)
          (map :listen)
          (viz/song-grid))
     [:h2.p-5.text-3xl  "Top 10 songs of the year"]
     (->> (db/track-counts (time/start-of-year))
          (take 10)
          (map :listen)
          (viz/song-grid))
     [:h2.p-5.text-3xl  "Top 10 songs of all time"]
     (->> (db/track-counts)
          (take 10)
          (map :listen)
          (viz/song-grid))
     [:h2.p-5.text-3xl  "10 random songs"]
     (->> (db/listens)
          (shuffle)
          (take 10)
          (viz/song-grid))
     [:h2.p-5.text-3xl "Top 10 albums of the week"]
     (->> (db/album-counts (time/start-of-week))
          (take 10)
          (map :listen)
          (map #(dissoc % :track-name :recording-mbid))
          (viz/song-grid))
     [:h2.p-5.text-3xl "Top 10 albums of the month"]
     (->> (db/album-counts (time/start-of-month))
          (take 10)
          (map :listen)
          (map #(dissoc % :track-name :recording-mbid))
          (viz/song-grid))
     [:h2.p-5.text-3xl  "Top 10 albums of the year"]
     (->> (db/album-counts (time/start-of-year))
          (take 10)
          (map :listen)
          (map #(dissoc % :track-name :recording-mbid))
          (viz/song-grid))
     [:h2.p-5.text-3xl  "Top 10 albums of all time"]
     (->> (db/album-counts)
          (take 10)
          (map :listen)
          (map #(dissoc % :track-name :recording-mbid))
          (viz/song-grid))
     [:h2.p-5.text-3xl "Top 10 artists of the week"]
     (->> (db/artist-counts (time/start-of-week))
          (take 10)
          (map :artist-name)
          (viz/artist-list))
     [:h2.p-5.text-3xl "Top 10 artists of the month"]
     (->> (db/artist-counts (time/start-of-month))
          (take 10)
          (map :artist-name)
          (viz/artist-list))
     [:h2.p-5.text-3xl  "Top 10 artists of the year"]
     (->> (db/artist-counts (time/start-of-year))
          (take 10)
          (map :artist-name)
          (viz/artist-list))
     [:h2.p-5.text-3xl  "Top 10 artists of all time"]
     (->> (db/artist-counts)
          (take 10)
          (map :artist-name)
          (viz/artist-list))
     [:footer.p-5
      [:p.text-slate-300 "Powered by " [:a.underline {:href "https://github.com/Sohalt/scrobble-server"} "https://github.com/Sohalt/scrobble-server"]]]]]))
