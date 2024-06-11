(ns com.pasaporte.schema)

    ;; :user/given-name given-name
    ;; :user/name name
    ;; :user/email email}])

(def schema
  {:user/id :uuid
   :user [:map {:closed true}
          [:xt/id           :user/id]
          [:user/given-name :string]
          [:user/name       :string]
          [:user/email      :string]
          [:user/joined-at  inst?]]

   :msg/id :uuid
   :msg [:map {:closed true}
         [:xt/id       :msg/id]
         [:msg/user    :user/id]
         [:msg/text    :string]
         [:msg/sent-at inst?]]})

(def module
  {:schema schema})
