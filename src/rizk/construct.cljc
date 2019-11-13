(ns rizk.construct
  (:require [ysera.test :refer [is= is is-not error?]]
            [ysera.error :refer [error]]
            [rizk.util :refer [int-or-else]]
            [rizk.definitions :refer [get-all-tile-defns
                                      get-tile-defn
                                      get-group-defn
                                      get-group-defns]]
            [rizk.random :refer [random-partition-with-seed]]
            [clojure.set :refer [difference]]))

(defn create-empty-state
  "Creates an empty state."
  {:test (fn []
           (is= (create-empty-state 2)
                {:player-in-turn             "p1"
                 :turn-phase                 :reinforcement-phase
                 :seed                       0
                 :players                    ["p1" "p2"]
                 :tiles                      {}
                 :initial-army-size          20
                 :initial-reinforcement-size 3}))}
  [num-players]
  {:pre [(int? num-players) (>= num-players 2)]}
  {:player-in-turn             "p1"
   :turn-phase                 :reinforcement-phase
   :seed                       0
   :players                    (->> (range 1 (inc num-players))
                                    (map (fn [n] (str "p" n))))
   :tiles                      {}
   :initial-army-size          20
   :initial-reinforcement-size 3})

(defn create-tile
  "Creates a tile without owner-id."
  {:test (fn []
           (is= (create-tile "i")
                {:name        "i"
                 :troop-count 1})
           (is= (create-tile "i" :troop-count 2)
                {:name        "i"
                 :troop-count 2})
           (error? (create-tile "Williamstown")))}
  [tile-name & kvs]
  (let [definition (get-tile-defn tile-name)
        {troop-count :troop-count} kvs
        tile {:name        tile-name
              :troop-count (int-or-else troop-count 1)}]
    (if (nil? definition)
      (error "Couldn't get definition of " tile-name ". Are definitions loaded?")
      (if (empty? kvs)
        tile
        (apply assoc tile kvs)))))

(defn update-turn-phase
  "Updates the turn phase."
  {:test (fn []
           (is= (-> (create-empty-state 2)
                    (update-turn-phase :attack-phase)
                    (:turn-phase))
                :attack-phase)
           (is= (-> (create-empty-state 2)
                    (update-turn-phase (fn [_] :attack-phase))
                    (:turn-phase))
                :attack-phase))}
  [state fn-or-val]
  {:pre [(map? state) (or (fn? fn-or-val) (keyword? fn-or-val))]}
  (if (fn? fn-or-val)
    (update state :turn-phase fn-or-val)
    (assoc state :turn-phase fn-or-val)))

(defn active-player-id
  "Returns the id of the player in turn."
  {:test (fn []
           (is= (-> (create-empty-state 2)
                    (:player-in-turn))
                "p1"))}
  [state]
  (:player-in-turn state))

(defn players
  "Returns the list of player names."
  {:test (fn []
           (is= (->> (create-empty-state 3)
                     (players))
                ["p1" "p2" "p3"]))}
  [state]
  {:pre [(map? state)]}
  (:players state))

(defn player-count
  "Returns the number of players in the state."
  {:test (fn []
           (is= (-> (create-empty-state 3)
                    (player-count))
                3))}
  [state]
  {:pre [(map? state)]}
  (count (players state)))

(defn opponent-ids
  "Returns the ids of all opponents of the input player."
  {:test (fn []
           (is= (-> (create-empty-state 2)
                    (opponent-ids "p1"))
                ["p2"])
           (is= (-> (create-empty-state 9)
                    (opponent-ids "p5")
                    (sort))
                ["p1" "p2" "p3" "p4"
                 "p6" "p7" "p8" "p9"]))}
  [state player-id]
  {:pre [(map? state) (string? player-id)]}
  (->> (players state)
       (remove (fn [p] (= p player-id)))))

(defn get-tiles
  "Returns all tiles in the state or, optionally,
  in a given player's possession."
  {:test (fn []
           (is= (-> (create-empty-state 2)
                    (get-tiles))
                [])
           (is= (-> (create-empty-state 2)
                    (get-tiles "p1"))
                []))}
  ([state]
   {:pre [(map? state)]}
   (-> (:tiles state)
       (vals)
       (vec)))
  ([state player-id]
   {:pre [(map? state) (string? player-id)]}
   (->> (get-tiles state)
        (filter (fn [tile]
                  (= (:owner-id tile
                       player-id)))))))

(defn add-tile
  "Adds a tile to the state."
  {:test (fn []
           (is= (as-> (create-empty-state 3) $
                      (add-tile $ "p1" (create-tile "i"))
                      (get-tiles $ "p1")
                      (map :name $))
                ["i"]))}
  [state player-id tile]
  {:pre [(map? state) (string? player-id) (map? tile)]}
  (assoc-in state [:tiles (:name tile)]
            (assoc tile :owner-id player-id)))

(defn add-tiles
  "Adds a collection of tiles to the state."
  {:test (fn []
           (let [state (-> (create-empty-state 3)
                           (add-tiles "p1" [(create-tile "i")
                                            (create-tile "ii")]))
                 tiles (get-tiles state)]
             (is= (map :owner-id tiles)
                  ["p1" "p1"])
             (is= (map :name tiles)
                  ["i" "ii"])))}
  [state owner-id tiles]
  {:pre [(map? state) (string? owner-id) (every? map? tiles)]}
  (reduce (fn [state tile]
            (add-tile state owner-id tile))
          state
          tiles))

(defn get-tile
  "Returns the tile in the state identified by tile-name."
  {:test (fn []
           (is= (-> (create-empty-state 2)
                    (add-tile "p1" (create-tile "i"))
                    (get-tile "i"))
                {:name        "i"
                 :owner-id    "p1"
                 :troop-count 1}))}
  [state tile-name]
  {:pre [(map? state) (string? tile-name)]}
  (get-in state [:tiles tile-name]))

(defn get-in-tile
  "Returns the value associated with the key in the tile."
  {:test (fn []
           (is= (-> (create-empty-state 2)
                    (add-tile "p1" (create-tile "i"))
                    (get-in-tile "i" :owner-id))
                "p1"))}
  [state tile-name key]
  {:pre [(map? state) (string? tile-name) (keyword? key)]}
  (get-in state [:tiles tile-name key]))

(defn replace-tile
  "Adds new-tile into the state, removing any other tile that shares the same name."
  {:test (fn []
           (let [new-tile (create-tile "i"
                                       :troop-count 5
                                       :owner-id "p2")]
             (is= (-> (create-empty-state 2)
                      (add-tile "p1" (create-tile "i"))
                      (replace-tile new-tile)
                      (get-tile "i"))
                  new-tile)))}
  [state new-tile]
  {:pre [(map? state) (map? new-tile)]}
  (assoc-in state [:tiles (:name new-tile)] new-tile))

(defn replace-tiles
  "Replaces multiple tiles."
  {:test (fn []
           (let [new-tiles [(create-tile "i"
                                         :troop-count 5
                                         :owner-id "p2")
                            (create-tile "ii"
                                         :troop-count 7
                                         :owner-id "p2")]
                 state (-> (create-empty-state 2)
                           (add-tiles "p1" [(create-tile "i")
                                            (create-tile "ii")])
                           (replace-tiles new-tiles))]
             (is= (->> (get-tiles state)
                       (filter (fn [n] (or (= "i" (:name n))
                                           (= "ii" (:name n))))))
                  new-tiles)))}
  [state new-tiles]
  {:pre [(map? state) (coll? new-tiles) (every? map? new-tiles)]}
  (reduce (fn [state new-tile]
            (replace-tile state new-tile))
          state
          new-tiles))

(defn update-tile
  "Updates a tile, given a key and either a function to apply to the current
  value, or a value to override to the current value with."
  {:test (fn []
           ; update owner
           (is= (-> (create-empty-state 2)
                    (add-tile "p1" (create-tile "i"))
                    (update-tile "i" :owner-id "p2")
                    (get-tile "i")
                    (:owner-id))
                "p2")
           ; update troop count
           (is= (-> (create-empty-state 2)
                    (add-tile "p1" (create-tile "i"))
                    (update-tile "i" :troop-count 3)
                    (get-tile "i")
                    (:troop-count))
                3)
           ; update with function
           (is= (-> (create-empty-state 2)
                    (add-tile "p1" (create-tile "i"))
                    (update-tile "i" :troop-count inc)
                    (get-tile "i")
                    (:troop-count))
                2))}
  [state tile-name key fn-or-val]
  {:pre [(map? state) (string? tile-name) (keyword? key) (or (fn? fn-or-val)
                                                             (pos-int? fn-or-val)
                                                             (string? fn-or-val))]}
  (let [tile (get-tile state tile-name)]
    (replace-tile state (if (fn? fn-or-val)
                          (update tile key fn-or-val)
                          (assoc tile key fn-or-val)))))

(defn randomly-assign-tiles
  "Randomly assigns tiles to the players in the game.
  Each assigned tile has 1 troop present.
  In the case that the number of players does not divide the number of
  tiles, no two players should have a tile-count differing by more than 1."
  {:test (fn []
           ; Check that no two players have tile-counts differing by more than 1
           (let [counts (->> (create-empty-state 3)
                             (randomly-assign-tiles)
                             (get-tiles)
                             (map :owner-id)
                             (frequencies)
                             (vals))]
             (is (<= (- (apply max counts)
                        (apply min counts))
                     1)))
           ; Check that all tiles are assigned
           (is (->> (create-empty-state 2)
                    (randomly-assign-tiles)
                    (get-tiles)
                    (every? (fn [t] (contains? t :owner-id)))
                    ))
           ; All tiles have 1 troop count
           (is (->> (create-empty-state 3)
                    (randomly-assign-tiles)
                    (get-tiles)
                    (every? (fn [t] (= (:troop-count t) 1)))))
           ; Randomly assign on subset of tiles
           (is= (->> (randomly-assign-tiles (create-empty-state 3)
                                            ["i" "ii"])
                     (get-tiles)
                     (map :name))
                ["i" "ii"]))}
  ([state]
   {:pre [(map? state)]}
   (->> (get-all-tile-defns)
        (map :name)
        (randomly-assign-tiles state)))
  ([state tile-names]
   {:pre [(map? state) (every? string? tile-names)]}
   (let [seed (:seed state)
         player-count (player-count state)
         [seed tile-name-partns] (random-partition-with-seed seed player-count tile-names)
         state (assoc state :seed seed)                     ;update seed in state
         indexed-partns (map-indexed (fn [index part] {:player-id (str "p" (inc index))
                                                       :partition part})
                                     tile-name-partns)]
     (reduce (fn [state {id         :player-id
                         tile-names :partition}]
               (add-tiles state id (map create-tile tile-names)))
             state
             indexed-partns))))

(defn create-game
  "Creates a starting game state."
  {:test (fn []
           (is= (create-game 2 [{:tiles [(create-tile "i" :troop-count 10)
                                         "ii"]}
                                {:tiles ["iii" "iv"]}]
                             :initial-army-size 30)
                {:player-in-turn             "p1"
                 :turn-phase                 :reinforcement-phase
                 :seed                       -9203025489357073502
                 :players                    ["p1" "p2"]
                 :tiles                      {"i"   {:name        "i"
                                                     :owner-id    "p1"
                                                     :troop-count 10}
                                              "ii"  {:name        "ii"
                                                     :owner-id    "p1"
                                                     :troop-count 1}
                                              "iii" {:name        "iii"
                                                     :owner-id    "p2"
                                                     :troop-count 1}
                                              "iv"  {:name        "iv"
                                                     :owner-id    "p2"
                                                     :troop-count 1}}
                 :initial-army-size          30
                 :initial-reinforcement-size 3}))}
  ([num-players]
   {:pre [(>= num-players 2)]}
   (-> (create-empty-state num-players)
       (randomly-assign-tiles)))
  ([num-players data & kvs]
   {:pre [(>= num-players 2) (vector? data)]}
   (let [players-data (map-indexed (fn [index player-data]
                                     (assoc player-data :player-id (str "p" (inc index))))
                                   data)
         state (as-> (create-game num-players) $
                     (reduce (fn [state {player-id :player-id
                                         tiles     :tiles}]
                               (let [tiles (map (fn [tile]
                                                  (if (string? tile)
                                                    (create-tile tile :owner-id player-id)
                                                    (assoc tile :owner-id player-id)))
                                                tiles)]
                                 (replace-tiles state tiles)))
                             $
                             players-data))]
     (if (empty? kvs)
       state
       (apply assoc state kvs)))))

(defn neighbor-names
  "Returns the names of all neighbors of the tile with the given name."
  {:test (fn []
           (is= (neighbor-names "i")
                ["ii" "iv"]))}
  [tile-name]
  {:pre [(string? tile-name)]}
  (let [tile-defn (get-tile-defn tile-name)]
    (:neighbors tile-defn)))

(defn neighbors?
  "Returns true if the two tiles are neighbors, false otherwise."
  {:test (fn []
           (is (neighbors? "i"
                           "ii"))
           (is-not (neighbors? "i"
                               "iii")))}
  [tile-1-name tile-2-name]
  {:pre [(string? tile-1-name) (string? tile-2-name)]}
  (->> (neighbor-names tile-1-name)
       (filter (fn [n] (= n tile-2-name)))
       (first)
       (some?)))

(defn owns-group?
  "Checks if a player owns a group."
  {:test (fn []
           (is-not (-> (create-game 2)
                       (owns-group? "p1" "square")))
           (is (-> (create-game 2 [{:tiles ["i" "ii" "iii" "iv"]}])
                   (owns-group? "p1" "square"))))}
  [state player-id group-name]
  {:pre [(map? state) (string? player-id) (string? group-name)]}
  (->> (get-group-defn group-name)
       (:member-tiles)
       (map (fn [name] (get-tile state name)))
       (map :owner-id)
       (filter (fn [owner-id] (not= owner-id
                                    player-id)))
       (empty?)))

(defn get-owned-groups
  "Returns the groups owned by the player."
  {:test (fn []
           (is= (-> (create-game 2)
                    (get-owned-groups "p1"))
                [])
           (is= (-> (create-game 2 [{:tiles ["i" "ii" "iii" "iv"]}])
                    (get-owned-groups "p1"))
                ["square"]))}
  [state player-id]
  {:pre [(map? state) (string? player-id)]}
  (let [group-names (map :name (get-group-defns))]
    (filter (fn [group-name]
              (owns-group? state player-id group-name))
            group-names)))