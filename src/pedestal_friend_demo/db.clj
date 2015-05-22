(ns pedestal-friend-demo.db
  (:require [korma
             [db :as kdb]
             [core :as kc]]
            [pedestal-friend-demo.conf :refer (pepper)]
            [cemerick.friend.credentials :refer (hash-bcrypt)]))

;; DB Entity Definitions
;; (kdb/defdb db (kdb/postgres {:user "user_name" :password "db_pass" :db "db_name"}))

;;uncomment above lines to get db connection




;;;;;;;;;;;;;;;;;;;;;;
;; Auth stuff Start ;;
;;;;;;;;;;;;;;;;;;;;;;


(def users (atom {"manager" {:username "manager" :password (hash-bcrypt (str "mpass" pepper))
                             :roles #{:pedestal-friend-demo.service/manager}}
                  "admin" {:username "admin" :password (hash-bcrypt (str "apass" pepper))
                           :roles #{:pedestal-friend-demo.service/admin}}}))

(defn get-user-auth [username] (@users username))
