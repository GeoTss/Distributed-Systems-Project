package com.example.client_efood.Domain;

import java.io.Serializable;

public class Utils {
    public static class Pair<K, V> implements Serializable {
        private static final long serialVersionUID = 10L;

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
