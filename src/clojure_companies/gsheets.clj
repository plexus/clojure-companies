(ns clojure-companies.gsheets
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [com.google.api.client.auth.oauth2 AuthorizationCodeFlow Credential]
           com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
           com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
           [com.google.api.client.googleapis.auth.oauth2 GoogleAuthorizationCodeFlow$Builder GoogleClientSecrets]
           com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
           com.google.api.client.json.jackson2.JacksonFactory
           com.google.api.client.util.store.FileDataStoreFactory
           [com.google.api.services.sheets.v4 Sheets Sheets$Builder SheetsScopes]
           [com.google.api.services.sheets.v4.model Spreadsheet SpreadsheetProperties]))

(def credentials-path "clojure_companies/gsheets_credentials.json")
(def tokens-directory-path "target/tokens")
(def sheet-id "1jBQD-rzOeGeKgLjsQ21r4YfEHp8XOpB_vl6TGJEBj3g")
(def app-name "Clojure Companies")

(defonce JSON_FACTORY (JacksonFactory/getDefaultInstance))
(defonce HTTP_TRANSPORT (GoogleNetHttpTransport/newTrustedTransport))
(defonce READ_ONLY SheetsScopes/SPREADSHEETS_READONLY)

(defn ^GoogleClientSecrets client-secrets
  "Read JSON file as provided by Google."
  [path]
  (->> path
       io/resource
       io/reader
       (GoogleClientSecrets/load JSON_FACTORY)))

(defn ^AuthorizationCodeFlow auth-code-flow
  "Construct an AuthorizationCodeFlow."
  [^GoogleClientSecrets credential-secrets ^java.util.List scopes]
  (-> (GoogleAuthorizationCodeFlow$Builder. HTTP_TRANSPORT JSON_FACTORY credential-secrets scopes)
      (.setDataStoreFactory (FileDataStoreFactory. (io/file tokens-directory-path)))
      (.setAccessType "offline")
      (.build)))

(defn ^Credential authorize!
  "Pops up browser for OAuth2 flow, returns credentials."
  [credentials-path scopes]
  (-> (client-secrets credentials-path)
      (auth-code-flow scopes)
      (AuthorizationCodeInstalledApp. (LocalServerReceiver.))
      (.authorize "user")))

(defn ^Sheets sheet-service [^Credential credential]
  (let [credential (authorize! credentials-path [READ_ONLY])]
    (-> (Sheets$Builder. HTTP_TRANSPORT JSON_FACTORY credential)
        (.setApplicationName app-name)
        (.build))))



(defn ^Spreadsheet spreadsheet [^Sheets service ^String spreadsheet-id]
  (-> service
      .spreadsheets
      (.get spreadsheet-id)
      (.set "includeGridData" true)
      .execute))

(defmulti goog->clj type)

(def map-goog->clj (map goog->clj))
(def map-goog->clj-kv (map (fn [[k v]] [(goog->clj k) (goog->clj v)])))

(defmethod goog->clj Spreadsheet [s]
  (into {} map-goog->clj-kv s))

(defmethod goog->clj SpreadsheetProperties [s]
  (into {} map-goog->clj-kv s))

(defmethod goog->clj java.util.List [l]
  (into [] map-goog->clj l))

(defmethod goog->clj java.util.Map [m]
  (into {} map-goog->clj-kv m))

(defmethod goog->clj java.lang.String [s] s)
(defmethod goog->clj java.lang.Float [f] f)
(defmethod goog->clj java.lang.Integer [i] i)
(defmethod goog->clj java.lang.Boolean [b] b)

(defn rows-data [rows]
  (let [cell-val (fn [v] (some-> v (get "effectiveValue") first val))
        [headers & rows] rows
        headers (->> (get headers "values") (map cell-val) (take-while identity))]
    (into [] (comp (map #(get % "values"))
                   (map (partial map cell-val))
                   (map (partial zipmap headers))
                   (filter seq))
          rows)))

(defn sheets-data
  "Massage sheet data into more sensible EDN maps/vectors."
  [spreadsheet]
  (let [sheets (get spreadsheet "sheets")]
    (into {}
          (map (fn [sheet]
                 [(get-in sheet ["properties" "title"])
                  (rows-data (get-in sheet ["data" 0 "rowData"]))]))
          sheets)))

(defn clojure-company-data []
  (let [service     (-> (authorize! credentials-path [READ_ONLY])
                        sheet-service)
        spreadsheet (spreadsheet service sheet-id)]
    (mapcat (fn [[continent companies]]
              (->> companies
                   (map #(assoc % "Continent" continent))
                   (map (fn [c] (into {} (map (fn [[k v]] [(keyword (str/lower-case k)) v])) c)))))
            (sheets-data (goog->clj spreadsheet)))))

(comment
  (take 2 (clojure-company-data)))
;; => ({:company "Diagnosia",
;;      :country "Austria",
;;      :city "Vienna",
;;      :url "https://www.diagnosia.com/",
;;      :twitter nil,
;;      :linkedin nil,
;;      :product/agency "Medical app & database",
;;      :continent "Europe"}
;;     {:company "handcheque",
;;      :country "Austria",
;;      :city "Vienna",
;;      :url "https://handcheque.com/",
;;      :twitter nil,
;;      :linkedin nil,
;;      :product/agency "Credit Card with display",
;;      :continent "Europe"})
