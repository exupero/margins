(ns margins.queries)

(def dependency
  '[[(dependency ?e ?e2)
     [?e :cell/dependencies ?deps]
     [?e :cell/notebook ?n]
     [?e2 :cell/name ?nm]
     [?e2 :cell/notebook ?n]
     [(contains? ?deps ?nm)]]
    [(dependency ?e ?e2)
     [?e :cell/dependencies ?deps]
     [?e :cell/notebook ?n]
     [?e1 :cell/name ?nm]
     [?e1 :cell/notebook ?n]
     [(contains? ?deps ?nm)]
     (dependency ?e1 ?e2)]])

(def dependent
  '[[(dependent ?e ?nm)
     [?e :cell/dependencies ?deps]
     [(contains? ?deps ?nm)]]
    [(dependent ?e ?nm)
     [?e :cell/dependencies ?deps]
     [?e2 :cell/name ?nm2]
     [(contains? ?deps ?nm2)]
     (dependent ?e2 ?nm)]])
