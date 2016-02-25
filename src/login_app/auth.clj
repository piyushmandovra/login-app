(ns login-app.auth
  (:require [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.http.ring-middlewares :as middlewares]
            [ring.util.response :as ring-resp]
            [cemerick.friend :as friend]
            [cemerick.friend
             [credentials :as creds]
             [workflows :as workflows]]
            [io.pedestal.log :as log]
            [login-app.db :as db]
            [login-app.conf :as conf]))

(defn peppered-bcrypt-credential-fn
  [load-credentials-fn {:keys [username password]}]
  (prn "[load-credentials-fn {:keys [username password]}]" load-credentials-fn username password)
  (when-let [credentials (load-credentials-fn username)]
    (let [password-key (or (-> credentials meta ::password-key) :password)]
      (when (creds/bcrypt-verify (str password conf/pepper) (get credentials password-key))
        (log/info credentials)
        (dissoc credentials password-key)))))

(defn unauth [_]
  (ring-resp/status (ring-resp/response "You do not have sufficient privileges to access ") 401))

(defn login-fail [_]
  (ring-resp/response "<html> <a> Invalid username password</a>
<br> to retry <a href=\"/login\"> Login</a>
<br> <a><b>For Admin Role:</b> admin, apass</a>
<br> <a><b>For Manager Role:</b> manager, mpass</a>
</html> "))

(def friend-config
  "A friend config for interactive form use."
  {:login-uri "/login"
   :default-landing-uri "/authorized"
   :workflows [(workflows/interactive-form)]
   :credential-fn (partial peppered-bcrypt-credential-fn db/get-user-auth)
   :unauthorized-handler unauth
   :login-failure-handler login-fail})

(defn friend-authenticate-interceptor
    "Creates a friend/authenticate interceptor for a given config."
    [auth-config]
    (interceptor
      {:error
      (fn [{:keys [request] :as context} exception]
        ;; get exception details without Slingshot in this sample
        (let [exdata (ex-data exception)
              extype (-> exdata :object :cemerick.friend/type)]
          (if (#{:unauthorized} extype)
            ;; unauthorized errors should generate a response using catch handler
            (let [handler-map (:friend/handler-map request)
                  response ((:catch-handler handler-map) ;handler to use
                            (assoc (:request handler-map) ;feed exception back in
                              :cemerick.friend/authorization-failure exdata))]
              ;; respond with generated response
              (assoc context :response response))
            ;; re-throw other errors
            (throw exception))))
      :enter
      (fn [{:keys [request] :as context}]
        (let [response-or-handler-map
              (friend/authenticate-request request auth-config)]
          ;; a handler-map will exist in authenticated request if authenticated
          (if-let [handler-map (:friend/handler-map response-or-handler-map)]
            ;; friend authenticated the request, so continue
            (assoc-in context [:request :friend/handler-map] handler-map)
            ;; friend generated a response instead, so respond with it
            (assoc context :response response-or-handler-map))))
      :leave
      ;; hook up friend response handling
      (middlewares/response-fn-adapter friend/authenticate-response)}))

(defn friend-authorize-interceptor
  "Creates a friend interceptor for friend/authorize."
  [roles]
  (interceptor
    {:enter
    (fn [{:keys [request] :as context}]
      (let [auth (:auth (:friend/handler-map request))]
        ;; check user has an authorized role
        (if (friend/authorized? roles auth)
          ;; authorized, so continue
          context
          ;; unauthorized, so throw exception for authentication interceptor
          (friend/throw-unauthorized auth {:cemerick.friend/required-roles roles}))))}))
