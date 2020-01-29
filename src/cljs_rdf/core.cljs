;; Copyright © 2020 pukkamustard <pukkamustard@posteo.net>
;;
;; This file is part of GeoPub.
;;
;; GeoPub is free software: you can redistribute it and/or modify
;; it under the terms of the GNU General Public License as published by
;; the Free Software Foundation, either version 3 of the License, or
;; (at your option) any later version.
;;
;; GeoPub is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU General Public License for more details.
;;
;; You should have received a copy of the GNU General Public License
;; along with GeoPub.  If not, see <https://www.gnu.org/licenses/>.

(ns cljs-rdf.core
  "RDF in ClojureScript"
  (:require-macros [cljs-rdf.core :refer [defns]]
                   [cljs-rdf.core :as rdf])
  (:require [cljs.core.logic :as l]))

;; Commonly used namespaces

(defns rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#")

(defns xsd "http://www.w3.org/2001/XMLSchema#")

;; Basic Interfaces

(defprotocol ITriple
  (triple-subject [x])
  (triple-predicate [x])
  (triple-object [x]))

(defn triple? [x]
  (satisfies? ITriple x))

(defprotocol IIRI
  (iri-value [x]))

(defn iri? [x]
  (satisfies? IIRI x))

(defprotocol IBlankNode
  (blank-node-id [x]))

(defn blank-node? [x]
  (satisfies? IBlankNode x))

(defprotocol ILiteral
  (literal-value [x])
  (literal-language [x])
  (literal-datatype [x]))

(defn literal? [x]
  (satisfies? ILiteral x))

;; Graph

(defprotocol IGraph
  (graph-add [x triple] "Add a triple to the dataset.")
  (graph-delete [x triple] "Remove triple from the dataset.")
  (graph-has [x triple] "Returns true if triple is in dataset, false if not.")
  (graph-match [x q] "Returns sequence of triples matching query")
  (graph-tripleo [x q] "Unify graph into a relational program"))

(defn graph? [x]
  (satisfies? IGraph x))

(defn graph-rel [seq q]
  "Unify graph into a relational program."
  (fn [s]
    (l/to-stream
     ;; TODO plenty of room for optimizing. Currently just treats graph as a seq.
     (map #(l/unify s % q) seq))))

;; ;; Implement protocol for basic types

(extend-type js/String
  IIRI
  (iri-value [x] x)

  ILiteral
  (literal-value [x] (.valueOf x))
  (literal-language [x] nil)
  (literal-datatype [x] (xsd "string")))

(extend-type js/Number
  ILiteral
  (literal-value [x] (.valueOf x))
  (literal-language [x] nil)
  (literal-datatype [x]
    (do
      (cond
        (integer? (.valueOf x)) (xsd "integer")
        (double? (.valueOf x)) (xsd "double")))))


;; Implementation of data model using records


(declare ->IRI)

(defrecord IRI [value]
  IIRI
  (iri-value [v] (:value v))

  l/IUnifyTerms
  (l/-unify-terms [u v s]
    (if (instance? IRI v)
      (l/unify s (:value u) (:value v))
      (l/unify s v u)))

  l/IWalkTerm
  (l/-walk-term [v s]
    (->IRI
     (l/-walk* s (:value v))))

  l/IReifyTerm
  (l/-reify-term [v s]
    (l/-reify* s (:value v))))

(defn iri [v]
  (cond
    (instance? IRI v) v

    (iri? v) (->IRI (str (iri-value v)))

    :else (->IRI v)))

(declare ->BlankNode)

(defrecord BlankNode [id]
  IBlankNode
  (blank-node-id [b] (:id b))

  l/IUnifyTerms
  (l/-unify-terms [u v s]
    (if (instance? BlankNode v)
      (l/unify s (:id u) (:id v))
      (l/unify s v u)))

  l/IWalkTerm
  (l/-walk-term [v s]
    (->BlankNode
     (l/-walk* s (:id v))))

  l/IReifyTerm
  (l/-reify-term [v s]
    (l/-reify* s (:id v))))

(defn blank-node
  "Returns a blank node. If no id is suplied a new (and unique) id will be generated."
  ([] (->BlankNode (gensym)))
  ([v]
   (cond
     (instance? BlankNode v) v
     (blank-node? v) (->BlankNode (blank-node-id v))
     :else (->BlankNode v))))

(declare ->Literal)

(defrecord Literal [value language datatype]
  ILiteral
  (literal-value [l] (:value l))
  (literal-language [l] (:language l))
  (literal-datatype [l] (:datatype l))

  l/IUnifyTerms
  (-unify-terms [u v s]
    (if (instance? Literal v)
      (-> s
          (l/unify (:value u) (:value v))
          (l/unify (:language u) (:language v))
          (l/unify (:datatype u) (:datatype v)))
      (l/unify s v u)))

  l/IWalkTerm
  (l/-walk-term [v s]
    (->Literal
     (l/-walk* s (:value v))
     (l/-walk* s (:language v))
     (l/-walk* s (:datatype v))))

  l/IReifyTerm
  (l/-reify-term [v s]
    (-> s
        (l/-reify* (:value v))
        (l/-reify* (:language v))
        (l/-reify* (:datatype v)))))

(defn literal
  "Returns a literal with optional language and datatype."
  ([value & {:keys [language datatype]}]

   (cond

     ;; already a literal, nothing to do
     (instance? Literal value) value

     ;; satisfies the ILiteral protocol, cast to a Literal
     (literal? value)
     (->Literal (literal-value value)
                (str (or language (literal-language value)))
                (iri (or datatype (literal-datatype value))))

     ;; return a new Literal
     :else (->Literal value language datatype))))

(declare ->Triple)

(defrecord Triple [subject predicate object]
  ITriple
  (triple-subject [q] (:subject q))
  (triple-predicate [q] (:predicate q))
  (triple-object [q] (:object q))

  l/IUnifyTerms
  (l/-unify-terms [u v s]
    (if (instance? Triple v)
      (-> s
          (l/unify (:subject u) (:subject v))
          (l/unify (:predicate u) (:predicate v))
          (l/unify (:object u) (:object v)))
      (l/unify s v u)))

  l/IWalkTerm
  (l/-walk-term [v s]
    (->Triple
     (l/-walk* s (:subject v))
     (l/-walk* s (:predicate v))
     (l/-walk* s (:object v))))

  l/IReifyTerm
  (l/-reify-term [v s]
    (-> s
        (l/-reify* (:subject v))
        (l/-reify* (:predicate v))
        (l/-reify* (:object v)))))

(defn subject [s]
  "Return an IRI or a BlankNode"
  (cond
    (satisfies? IIRI s) (iri s)
    (satisfies? IBlankNode s) (blank-node s)
    :else s))

(defn predicate [p]
  "Returns an IRI"
  (cond
    (satisfies? IIRI p) (iri p)
    :else p))

(defn object [o]
  "Returns a Literal, IRI or BlankNode"
  (cond
    ;; Cast as literal before casting as IRI. This causes (object-cast "hello") to return a literal instead of an IRI.
    (satisfies? ILiteral o) (literal o)
    (satisfies? IIRI o) (iri o)
    (satisfies? IBlankNode o) (blank-node o)
    :else o))

(defn triple
  "Returns a triple."
  ([t] (cond
         (instance? Triple t) t

         (satisfies? ITriple t)
         (->Triple
          (subject (triple-subject t))
          (predicate (triple-predicate t))
          (object (triple-object t)))))

  ([s p o] (->Triple
            (subject s)
            (predicate p)
            (object o))))

(defn tripleo
  "Relation to match triple"
  [s p o t]
  (fn [a]
    (l/unify a (triple s p o) t)))

