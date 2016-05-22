(ns views.sql.core
  (:import
    (net.sf.jsqlparser.parser CCJSqlParserUtil)
    (net.sf.jsqlparser.util TablesNamesFinder)
    (net.sf.jsqlparser.statement Statement)
    (net.sf.jsqlparser.statement.update Update)
    (net.sf.jsqlparser.statement.delete Delete)
    (net.sf.jsqlparser.statement.insert Insert)
    (net.sf.jsqlparser.statement.select Select))
  (:require
    [clojure.java.jdbc :as jdbc]
    [views.core :as views]))

(def hint-type :sql-table-name)

(def ^:private query-types
  {Select :select
   Insert :insert
   Update :update
   Delete :delete})

(defn- get-query-tables-set
  [^Statement stmt]
  (as-> (TablesNamesFinder.) x
        (.getTableList x stmt)
        (map keyword x)
        (set x)))

(defn- sql-stmt-returning?
  [^Statement stmt stmt-type]
  (condp = stmt-type
    :select true
    :insert (let [return-expr    (.getReturningExpressionList ^Insert stmt)
                  returning-all? (.isReturningAllColumns ^Insert stmt)]
              (or returning-all?
                  (and return-expr
                       (not (.isEmpty return-expr)))))
    ; TODO: JSqlParser doesn't currently support PostgreSQL's RETURNING clause
    ;       support in UPDATE and DELETE queries
    :update false
    :delete false))

(defn- get-query-info
  [^String sql]
  (let [stmt      (CCJSqlParserUtil/parse sql)
        stmt-type (get query-types (type stmt))]
    (if-not stmt-type
      (throw (new Exception "Unsupported SQL query. Only SELECT, INSERT, UPDATE and DELETE queries are supported!"))
      {:type       stmt-type
       :returning? (sql-stmt-returning? stmt stmt-type)
       :tables     (get-query-tables-set stmt)})))

(defn query-info
  [^String sql]
  (if-let [info (get-in @views/view-system [:views-sql :cache sql])]
    info
    (let [info (get-query-info sql)]
      (swap! views/view-system assoc-in [:views-sql :cache sql] info)
      info)))

(defn query-tables
  [^String sql]
  (:tables (query-info sql)))

(defmacro with-view-transaction
  [binding & forms]
  (let [tvar (first binding)
        db   (second binding)
        args (drop 2 binding)]
    `(if (:views-sql/hints ~db) ;; check if we are in a nested transaction
       (let [~tvar ~db] ~@forms)
       (let [hints#   (atom [])
             result#  (jdbc/with-db-transaction [t# ~db ~@args]
                                                (let [~tvar (assoc ~db :views-sql/hints hints#)]
                                                  ~@forms))]
         (views/put-hints! @hints#)
         result#))))

(defn- execute-sql!
  [db [sql & params :as sqlvec]]
  (let [info (query-info sql)]
    (if (:returning? info)
      (jdbc/query db sqlvec)
      (jdbc/execute! db sqlvec))))

(defn vexec!
  ([db namespace [sql & params :as sqlvec]]
   (let [results (execute-sql! db sqlvec)
         hint    (views/hint namespace (query-tables sql) hint-type)]
     (if-let [tx-hints (:views-sql/hints db)]
       (swap! tx-hints conj hint)
       (views/put-hints! [hint]))
     results))
  ([db [sql & params :as sqlvec]]
   (vexec! db nil sqlvec)))
