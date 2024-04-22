(ns time
  (:import (java.time LocalDate ZoneId DayOfWeek)
           (java.time.temporal TemporalAdjusters)))

(defn start-of-week []
  (-> (LocalDate/now)
      (.with (TemporalAdjusters/previousOrSame DayOfWeek/MONDAY))
      (.atStartOfDay (ZoneId/systemDefault))
      (.toEpochSecond)))

(defn start-of-month []
  (-> (LocalDate/now)
      (.withDayOfMonth 1)
      (.atStartOfDay (ZoneId/systemDefault))
      (.toEpochSecond)))

(defn start-of-year []
  (-> (LocalDate/now)
      (.withDayOfYear 1)
      (.atStartOfDay (ZoneId/systemDefault))
      (.toEpochSecond)))
