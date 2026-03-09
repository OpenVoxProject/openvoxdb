(ns puppetlabs.puppetdb.time
  "Time-related Utility Functions

   This namespace contains utility functions for working with time objects,
   built on java.time.Instant (timestamps) and java.time.Duration (periods)."
  (:import (java.time Instant Duration ZonedDateTime ZoneOffset DateTimeException)
           (java.time.format DateTimeFormatter DateTimeFormatterBuilder DateTimeParseException)
           (java.time.temporal ChronoUnit)
           (java.sql Timestamp))
  (:require [schema.core :as s]))

(declare wire-datetime->instant)

;; Instant constructors

(defn now [] (.truncatedTo (Instant/now) ChronoUnit/MILLIS))

(defn date-time
  "Construct an Instant from year/month/day/hour/minute/second/millis (all UTC)."
  ([year month day]
   (date-time year month day 0 0 0 0))
  ([year month day hour]
   (date-time year month day hour 0 0 0))
  ([year month day hour minute]
   (date-time year month day hour minute 0 0))
  ([year month day hour minute second]
   (date-time year month day hour minute second 0))
  ([year month day hour minute second millis]
   (-> (ZonedDateTime/of year month day hour minute second (* millis 1000000) ZoneOffset/UTC)
       .toInstant)))

;; Duration constructors

(defn days [n] (Duration/ofDays n))
(defn hours [n] (Duration/ofHours n))
(defn minutes [n] (Duration/ofMinutes n))
(defn seconds [n] (Duration/ofSeconds n))
(defn millis [n] (Duration/ofMillis (long n)))

;; Arithmetic

(defn ago [^Duration d] (if d (.minus (now) d) (now)))
(defn from-now [^Duration d] (if d (.plus (now) d) (now)))
(defn plus [t d] (.plus t d))
(defn minus [t d] (.minus t d))

(defn interval
  "Returns the Duration between two Instants."
  [^Instant start ^Instant end]
  (Duration/between start end))

;; Truncation

(defn truncate-to-day
  "Truncate an Instant to the start of the day (UTC)."
  [^Instant t]
  (-> t (.atZone ZoneOffset/UTC) (.truncatedTo ChronoUnit/DAYS) .toInstant))

;; Comparison

(defn equal? [^Instant a ^Instant b] (.equals a b))
(defn before? [^Instant a ^Instant b] (.isBefore a b))
(defn after? [^Instant a ^Instant b] (.isAfter a b))

;; Predicates

(defn date-time? [x] (instance? Instant x))
(defn period? [x] (instance? Duration x))

;; Conversion to/from longs

(defn to-long
  "Convert a timestamp to epoch milliseconds."
  [t]
  (cond
    (instance? Instant t) (.toEpochMilli ^Instant t)
    (instance? Timestamp t) (.getTime ^Timestamp t)
    (string? t) (.toEpochMilli (Instant/parse t))
    (number? t) (long t)
    :else (throw (IllegalArgumentException.
                  (str "Cannot convert to long: " (type t))))))
(defn from-long [n] (Instant/ofEpochMilli (long n)))

;; Conversion from SQL types

(defn from-sql-date [^Timestamp ts] (.toInstant ts))

;; Wire format formatter: always outputs 3-digit milliseconds
(def ^DateTimeFormatter wire-format-formatter
  (-> (DateTimeFormatterBuilder.) (.appendInstant 3) .toFormatter))

(defn to-string
  "Convert a timestamp to ISO-8601 string with mandatory millisecond precision.
  Accepts Instant, java.sql.Timestamp, or String (parsed and reformatted to
  normalize timezone offset and precision)."
  [t]
  (cond
    (instance? Instant t) (.format wire-format-formatter ^Instant t)
    (instance? Timestamp t) (.format wire-format-formatter (.toInstant ^Timestamp t))
    (string? t) (when-let [parsed (wire-datetime->instant t)]
                  (.format wire-format-formatter ^Instant parsed))
    :else nil))

;; to-date-time: coerce various types to Instant

(defn to-date-time
  "Coerce x to an Instant. Accepts strings, longs, java.sql.Timestamps, and Instants."
  [x]
  (cond
    (instance? Instant x) x
    (instance? Timestamp x) (.toInstant ^Timestamp x)
    (string? x) (Instant/parse x)
    (number? x) (from-long x)
    :else (throw (IllegalArgumentException.
                  (str "Cannot convert to Instant: " (type x))))))

;; to-java-date protocol

(defprotocol ToJavaDate
  (to-java-date ^java.util.Date [x] "Converts x to a java.util.Date."))

(extend-protocol ToJavaDate
  java.time.Instant
  (to-java-date [x]
    (java.util.Date/from x)))

;; Period parsing/formatting

(defn parse-period
  "Parse a String into a Duration. Supported suffixes: d, h, m, s, ms.
  For example, (parse-period \"2d\") returns a Duration of 2 days."
  [s]
  {:pre  [(string? s)]
   :post [(period? %)]}
  (let [[_ n unit] (re-matches #"(\d+)(ms|[dhms])" s)]
    (when-not n
      (throw (IllegalArgumentException. (str "Cannot parse period: " s))))
    (case unit
      "d"  (Duration/ofDays (parse-long n))
      "h"  (Duration/ofHours (parse-long n))
      "m"  (Duration/ofMinutes (parse-long n))
      "s"  (Duration/ofSeconds (parse-long n))
      "ms" (Duration/ofMillis (parse-long n)))))

(defn format-period
  "Convert a Duration into a human-readable String, e.g. '2 minutes'.
  Normalizes to the largest clean unit."
  [p]
  {:pre  [(period? p)]
   :post [(string? %)]}
  (let [total-ms (.toMillis p)
        ms-per-day 86400000
        ms-per-hour 3600000
        ms-per-minute 60000
        ms-per-second 1000
        [n unit-s unit-p]
        (cond
          (and (pos? total-ms) (zero? (mod total-ms ms-per-day)))
          [(/ total-ms ms-per-day) "day" "days"]

          (and (pos? total-ms) (zero? (mod total-ms ms-per-hour)))
          [(/ total-ms ms-per-hour) "hour" "hours"]

          (and (pos? total-ms) (zero? (mod total-ms ms-per-minute)))
          [(/ total-ms ms-per-minute) "minute" "minutes"]

          (and (pos? total-ms) (zero? (mod total-ms ms-per-second)))
          [(/ total-ms ms-per-second) "second" "seconds"]

          :else
          [total-ms "millisecond" "milliseconds"])]
    (str n " " (if (= n 1) unit-s unit-p))))

;; Period comparison

(defn periods-equal?
  "Returns true if all given Durations represent the same amount of time."
  ([_p] true)
  ([p1 p2] (.equals p1 p2))
  ([p1 p2 & more]
   (if (periods-equal? p1 p2)
     (if (next more)
       (recur p2 (first more) (next more))
       (periods-equal? p2 (first more)))
     false)))

(defn period-longer?
  "Returns true if d1 is longer than d2."
  [^Duration d1 ^Duration d2]
  (pos? (.compareTo d1 d2)))

;; Period to numeric

(defn to-days [^Duration d] (.toDays d))
(defn to-hours [^Duration d] (.toHours d))
(defn to-minutes [^Duration d] (.toMinutes d))
(defn to-seconds [^Duration d] (.toSeconds d))
(defn to-millis [^Duration d] (.toMillis d))

;; Ordered formatters for timestamp parsing

(def ^:private ordered-format-parsers
  "Formatters ordered with more likely successful formats first."
  [DateTimeFormatter/ISO_DATE_TIME
   DateTimeFormatter/ISO_DATE
   DateTimeFormatter/BASIC_ISO_DATE])

(s/defn ^:always-validate attempt-date-time-parse
  "Parses `timestamp-str` using `formatter`. Returns nil if parsing fails."
  [formatter :- DateTimeFormatter
   timestamp-str :- String]
  (try
    (let [parsed (.parse formatter timestamp-str)]
      (or (try (Instant/from parsed) (catch DateTimeException _ nil))
          (try (.toInstant (.atOffset (java.time.LocalDateTime/from parsed) ZoneOffset/UTC))
               (catch DateTimeException _ nil))
          (try (.toInstant (.atStartOfDay (java.time.LocalDate/from parsed) ZoneOffset/UTC))
               (catch DateTimeException _ nil))))
    (catch DateTimeParseException _ nil)))

(s/defn ^:always-validate to-timestamp :- (s/maybe java.sql.Timestamp)
  "Convert to a java.sql.Timestamp. When `ts` is a String, attempts
  parsing with likely date formats first."
  [ts]
  (cond
    (instance? Timestamp ts) ts
    (instance? Instant ts) (Timestamp/from ts)
    (string? ts)
    (if-let [parsed (some #(attempt-date-time-parse % ts) ordered-format-parsers)]
      (Timestamp/from parsed)
      (try
        (Timestamp/from (from-long (Long/parseLong ts)))
        (catch NumberFormatException _ nil)))
    :else
    (when ts
      (Timestamp/from (to-date-time ts)))))

;; Parsing wire datetimes, e.g.
;;   api/wire_format/catalog_format_v9.markdown <datetime>

(defn parse-iso-z
  "Returns a pdb instant (UTC timestamp) if s represents an ISO
  formatted timestamp like \"2011-12-03T10:15:30Z\" or nil."
  [s]
  (try
    (Instant/parse s)
    (catch java.time.format.DateTimeParseException _
      nil)))

(def parse-offset-iso
  "Returns a pdb instant (UTC timestamp) if s represents an ISO
  formatted timestamp with offset like \"2011-12-03T10:15:30+01:00\"
  or nil."
  (let [formatter DateTimeFormatter/ISO_OFFSET_DATE_TIME]
    (fn [s]
      (try
        (-> (java.time.OffsetDateTime/parse s formatter) .toInstant)
        (catch java.time.format.DateTimeParseException _
          nil)))))

(defn wire-datetime->instant
  "Parses s as a PuppetDB wire format <datetime> and returns an
  Instant, or nil if the string cannot be parsed."
  [s]
  (when s (or (parse-iso-z s) (parse-offset-iso s))))

(defn wire-datetime?
  [s]
  (when (string? s) (wire-datetime->instant s)))

(defn ephemeral-now-ns
  "Returns the current time as *signed* integer nanoseconds with respect
  to some ephemeral time line.  Values are only comparable within the
  same process, and the range of values may be limited, but
  differences between successive calls within no more than 250 years
  of each other will be correct."
  []
  (System/nanoTime))
