package org.ClientSide.ClientStates.ClientStateArgs;

import org.Filters.Filter;
import org.StatePattern.StateArguments;

import java.util.*;

public class ApplyFiltersArgs implements StateArguments {
    public Set<Filter.Types> filter_types = new TreeSet<>();
    public Map<Filter.Types, Object> additional_filter_args = new HashMap<>();
}
