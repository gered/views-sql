(defproject gered/views-sql "0.1.0-SNAPSHOT"
  :description  "Plain SQL view implementation for views"
  :url          "https://github.com/gered/views-honeysql"

  :license      {:name "MIT License"
                 :url "http://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/tools.logging "0.3.1"]
                 [com.github.jsqlparser/jsqlparser "0.9.5"]]

  :profiles     {:provided
                 {:dependencies
                  [[org.clojure/clojure "1.8.0"]
                   [org.clojure/java.jdbc "0.6.1"]
                   [gered/views "1.5-SNAPSHOT"]]}})
