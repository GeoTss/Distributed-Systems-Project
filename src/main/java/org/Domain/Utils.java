package org.Domain;

import java.io.Serializable;

public class Utils {
    public static class Pair<K, V> implements Serializable {
        public K first;
        public V second;

        public Pair(K first, V second) {
            this.first = first;
            this.second = second;
        }

        public V getSecond(){
            return second;
        }

        @Override
        public String toString() {
            return "(" + first + ", " + second + ")";
        }
    }
}
