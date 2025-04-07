package org.Filters;

import java.util.Set;

public class SameCategory<T extends Categorisable> implements Filter {
    Set<String> _categories;
    public SameCategory(Set<String> categories) {
        _categories = categories;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean satisfy(Object obj) {
        return _categories.contains(((T) obj).getCategory());
    }

}
