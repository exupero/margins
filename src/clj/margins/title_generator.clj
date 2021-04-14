(ns margins.title-generator
  (:require [clojure.string :as string]))

; Word lists taken from https://github.com/JonyEpsilon/gorilla-repl/blob/47fe408ddf28dc5b87ae198336126b2ce1722674/resources/gorilla-repl-client/js/utils.js

(def adjectives
  ["affectionate" "amiable" "arrogant" "balmy" "barren" "benevolent" "billowing" "blessed" "breezy" "calm" "celestial"
   "charming" "combative" "composed" "condemned" "divine" "dry" "energized" "enigmatic" "exuberant" "flowing" "fluffy"
   "fluttering" "frightened" "fuscia" "gentle" "greasy" "grieving" "harmonious" "hollow" "homeless" "icy" "indigo"
   "inquisitive" "itchy" "joyful" "jubilant" "juicy" "khaki" "limitless" "lush" "mellow" "merciful" "merry" "mirthful"
   "moonlit" "mysterious" "natural" "outrageous" "pacific" "parched" "placid" "pleasant" "poised" "purring"
   "radioactive" "resilient" "scenic" "screeching" "sensitive" "serene" "snowy" "solitary" "spacial" "squealing" "stark"
   "stunning" "sunset" "talented" "tasteless" "teal" "thoughtless" "thriving" "tranquil" "tropical" "undisturbed"
   "unsightly" "unwavering" "uplifting" "voiceless" "wandering" "warm" "wealthy" "whispering" "withered" "wooden"
   "zealous"])

(def nouns
  ["abyss" "atoll" "aurora" "autumn" "badlands" "beach" "briars" "brook" "canopy" "canyon" "cavern" "chasm" "cliff"
   "cove" "crater" "creek" "darkness" "dawn" "desert" "dew" "dove" "drylands" "dusk" "farm" "fern" "firefly" "flowers"
   "fog" "foliage" "forest" "galaxy" "garden" "geyser" "grove" "hurricane" "iceberg" "lagoon" "lake" "leaves" "marsh"
   "meadow" "mist" "moss" "mountain" "oasis" "ocean" "peak" "pebble" "pine" "plateau" "pond" "reef" "reserve"
   "resonance" "sanctuary" "sands" "shelter" "silence" "smokescreen" "snowflake" "spring" "storm" "stream" "summer"
   "summit" "sunrise" "sunset" "sunshine" "surf" "swamp" "temple" "thorns" "tsunami" "tundra" "valley" "volcano"
   "waterfall" "willow" "winds" "winter"])

(defn new-title []
  (str (string/capitalize (rand-nth adjectives))
       " "
       (string/capitalize (rand-nth nouns))))
