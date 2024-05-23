(ns viz
  (:require [db]
            [time]
            [babashka.http-client :as http]
            [cheshire.core :as json]
            [ring.util.codec :as codec]
            [clojure.java.io :as io])
  (:import (java.time.format DateTimeParseException)))

(def api-root "https://musicbrainz.org/ws/2/")

(defn query-json' [url]
  (let [{:as res :keys [status body headers]}
        (http/get url {:as :stream
                       :headers {"Accept" "application/json"
                                 "User-Agent" "clerk-viz/0.0.1 (musicbrainz@sohalt.net)"}
                       :throw false})]
    (if (= status 200)
      (json/parse-stream (io/reader body) keyword)
      (if-let [wait (get headers "retry-after")]
        (do (Thread/sleep (* 1000 (parse-long wait)) )
            (recur url))
        res))))

(def query-json (memoize query-json'))

(defn lookup
  ([type mbid] (lookup type mbid nil))
  ([type mbid params]
   (query-json (str api-root type "/" mbid (when params (str "?" (codec/form-encode params)))))))

(defn track-name [listen]
  (or (some-> listen
              :recording-mbid
              (partial lookup "recording")
              :title)
      (:track-name listen)))

(defn album-name [listen]
  (or (some-> listen
              :release-mbid
              (partial lookup "release")
              :title)
      (:release-name listen)))

(defn artist-name [listen]
  (or (some->> listen
               :artist-mbids
               first
               (lookup "artist")
               :name)
      (:artist-name listen)))

(defn release-artwork-info [release-mbid]
  (->> (query-json (str "https://coverartarchive.org/release/" release-mbid))
       :images
       (filter :front)
       first))

(defn cover-art-url
  ([cover-art-info] (cover-art-url cover-art-info :small))
  ([cover-art-info size]
   (or (some->> cover-art-info
                :thumbnails
                size)
       (:image cover-art-info))))

(def placeholder-img [:div.bg-slate-300.w-48.h-48])

(defn release-has-artwork? [release]
  (some->> release
           :cover-art-archive
           :front))

(defn has-artwork? [listen]
  (some->> listen
           :release-mbid
           (lookup "release")
           (release-has-artwork?)))

(defn guess-release-for-listen [listen]
  (->> (lookup "recording" (:recording-mbid listen) {:inc "releases"})
       :releases
       (filter (fn [{:as release :keys [id status]}]
                 (and (= "Official" status)
                      (some-> (lookup "release" id) :cover-art-archive :front))))
       (first)))

(defn release [listen]
  (or (some->> listen :release-mbid (lookup "release"))
      (guess-release-for-listen listen)))

(defn release-group [listen]
  (-> (lookup "release" (:id (release listen)) {:inc "release-groups"})
      :release-group))

(defn release-group-artwork-info [release-group]
  (some->> release-group
           :id
           (str "https://coverartarchive.org/release-group/")
           (query-json)
           :images
           (filter :front)
           first))

(defn cover-art-img [listen]
  (let [release (release listen)
        artwork-info (if (release-has-artwork? release)
                       (release-artwork-info (:id release))
                       (let [rg (release-group listen)]
                         (release-group-artwork-info rg)))]
    (if artwork-info
      [:img {:src (cover-art-url artwork-info)}]
      placeholder-img)))

(defn normalize-year [y]
  (try (.getYear (java.time.LocalDate/parse y))
       (catch DateTimeParseException _ y)))

(defn year [listen]
  (some->> (lookup "release" (:release-mbid listen))
           :date
           normalize-year))

(defn remove-nil-vals [m]
  (into {} (filter (fn [[_k v]] (some? v)) m)))

(defn listen-info [listen]
  (-> {:year (year listen)
       :track-name (track-name listen)
       :album-name (album-name listen)
       :artist-name (artist-name listen)}
      remove-nil-vals))

(defn release-url [listen]
  (when-let [release-id (:release-mbid listen)]
    (str "https://musicbrainz.org/release/" release-id)))

(defn tile [{:keys [background-image
                    main-content
                    extra-content
                    link]}]
  (cond->> [:div.w-48.h-48.flex.flex-col.flex-none.relative.group
            background-image
            [:div.w-full.bg-black.p-5.text-center.z-10.opacity-70.absolute.bottom-0.group-hover:opacity-90.flex.flex-col.transition
             [:span.font-sans.text-white.text-opacity-100.drop-shadow-md.mb-2
              {:style {"filter" "drop-shadow(0 1px 1px rgba(0,0,0,0.75)"}
               :class [(when link "group-hover:underline")]}
              main-content]
             [:span.font-sans.text-white.text-opacity-100.drop-shadow-md.hidden.group-hover:inline.text-sm.text-slate-100
              {:style {"filter" "drop-shadow(0 1px 1px rgba(0,0,0,0.75)"}
               :class [(when link "group-hover:underline")]}
              extra-content]]]
    link (conj [:a {:href link}])))

(defn song-tile [listen]
  (let [release-url (release-url listen)
        {:keys [track-name album-name artist-name year]} (listen-info listen)]
    (tile {:background-image (cover-art-img listen)
           :main-content track-name
           :extra-content (str album-name (when year (str " (" year ")"))
                               " — "
                               artist-name)
           :link release-url})))

(defn album-tile [listen]
  (let [release-url (release-url listen)
        {:keys [album-name artist-name year]} (listen-info listen)]
    (tile {:background-image (cover-art-img listen)
           :main-content (str album-name (when year (str " (" year ")")))
           :extra-content artist-name
           :link release-url})))

(defn song-list-item [listen]
  (let [{:keys [track-name album-name artist-name year]} (listen-info listen)]
    [:div.flex.flex-row.h-12.align-center
     (cover-art-img listen)
     [:span.font-sans.text-black.block.bg-blue
      (str (when track-name (str track-name " — "))
           artist-name " — "
           album-name
           (when year (str " (" year ")")))]]))

(defn artist-img [artist-name]
  ;;TODO
  placeholder-img)

(defn artist-tile [artist-name]
  [:div.w-48.h-48.flex.flex-col.flex-none.relative.container
   (artist-img artist-name)
   [:div.w-full.bg-black.p-5.text-center.z-10.opacity-70.absolute.bottom-0
    [:span.font-sans.text-white.text-opacity-100.drop-shadow-md
     {:style {"filter" "drop-shadow(0 1px 1px rgba(0,0,0,0.75)"}}
     artist-name]]])

(defn date->epochsecond [date]
  (.getEpochSecond (java.time.Instant/parse date)))

(defn song-grid [songs]
  (into [:div.flex.flex-row.flex-wrap.gap-1] (map song-tile songs)))

(defn album-grid [songs]
  (into [:div.flex.flex-row.flex-wrap.gap-1] (map album-tile songs)))

(defn song-list [songs]
  (into [:div.flex.flex-col.gap-1] (map song-list-item songs)))

(defn artist-list-item [artist-name]
  [:li artist-name])

(defn artist-list [artists]
  (into [:ul] (map artist-list-item artists)))
