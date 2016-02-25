(ns login-app.service
  (:require [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [ring.util.response :as ring-resp]
            [login-app.auth :as auth]
            [cemerick.friend :as friend]
            [io.pedestal.http.ring-middlewares :as middlewares]
            [ring.middleware.session.cookie :as cookie]))

;; forward declarations of routing functions used in pages
(declare url-for form-action)
;; stub login action needed for routing purposes.
(def login-action (constantly nil))
;;; Regular page handlers. Basic string templates to keep things core

(defn home-page [_]
  (ring-resp/response "Hello World!"))

(defn login-page
  [{{:keys [username]} :params}]
  (let [{:keys [action method]} (form-action ::login-action)]
    (ring-resp/response
      (str "<html><body>"
           (format "<form action=\"%s\" method=\"%s\">" action method)
           "User: <input type=\"text\" name=\"username\" value=\"" username "\">"
           "<br/>"
           "Pass: <input type=\"password\" name=\"password\">"
           "<br/>"
           "<input type=\"submit\" value=\"Login\">"
           "</form>"
           "</body></html>"))))

(defn logout-page [_]
  ;; redirect home and have your friend logout the user
  (-> (ring-resp/redirect "/") friend/logout*))

(def current-authentication
  "Dig around for info about request user"
  (comp friend/current-authentication :auth :friend/handler-map))

(defn dashboard-page [request]
  (let [username (-> request current-authentication :username)
        logout-url (url-for ::logout-page)
        role (last(clojure.string/split (str (first (-> request current-authentication :roles))) #"/"))]
    (ring-resp/response  (format (str "<html><body>" "Hello %s!<br/>"
                  "Your username is \"%s\"</br>" "<a href=\"%s\">Logout</a>" "</body></html>")
                   role username logout-url))))

(defn admin-dashboard [request] (dashboard-page request))

(defn manager-dashboard [request] (dashboard-page request))

(defn authorized-redirect [request]
  (ring-resp/redirect
    (let [roles (-> request current-authentication :roles)]
      (str (last(clojure.string/split (str (first roles)) #"/"))"/dashboard"))))

(defroutes routes
   [[["/" {:get home-page}
      ;; Set default interceptors for /about and any other paths under /
      ^:interceptors [(body-params/body-params)
                      bootstrap/html-body
                      ;; fix for interactive-form workflow
                      middlewares/keyword-params
                      ;; session is required by interactive-form workflow
                      (middlewares/session {:store (cookie/cookie-store)})
                      ;; sample authenticate request
                      (auth/friend-authenticate-interceptor auth/friend-config)]
      ["/login" {:get login-page :post login-action}]
      ["/logout" {:get logout-page}]
      ["/authorized" {:get authorized-redirect}]
      ["/admin" ^:interceptors [(auth/friend-authorize-interceptor #{::admin})]
       ["/dashboard" {:get admin-dashboard}]]
      ["/manager" ^:interceptors [(auth/friend-authorize-interceptor #{::manager})]
         ["/dashboard" {:get manager-dashboard}]]
      ]]])

;; handy routing functions
(def url-for (route/url-for-routes routes))
(def form-action (route/form-action-for-routes routes))
;; Consumed by login-app.server/create-server
;; See bootstrap/default-interceptors for additional options you can configure

(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; :bootstrap/interceptors []
              ::bootstrap/routes routes
              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::bootstrap/allowed-origins ["scheme://host:port"]
              ;; Root for resource interceptor that is available by default.
              ::bootstrap/resource-path "/public"
              ;; Either :jetty or :tomcat (see comments in project.clj
              ;; to enable Tomcat)
              ::bootstrap/host "0.0.0.0"
              ::bootstrap/type :jetty
              ::bootstrap/port 8080})
