package org.cha0scollective.wwpg.extension;

import java.util.IdentityHashMap;

/** Identity-based union with the same first-reference/physical-earth preference. */
final class ReferenceGroups {
    private final IdentityHashMap<Object, Object> parents = new IdentityHashMap<>();

    Object root(Object reference) {
        Object current = reference;
        while (parents.containsKey(current)) current = parents.get(current);
        Object result = current;
        current = reference;
        while (parents.containsKey(current)) {
            Object next = parents.get(current);
            parents.put(current, result); current = next;
        }
        return result;
    }

    void unite(Object first, Object second, Object earth) {
        first = root(first); second = root(second);
        if (first == second) return;
        if (second == earth) parents.put(first, second);
        else parents.put(second, first);
    }
}
