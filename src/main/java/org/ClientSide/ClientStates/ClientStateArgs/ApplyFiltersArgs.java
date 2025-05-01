package org.ClientSide.ClientStates.ClientStateArgs;

import org.Filters.Filter;
import org.StatePattern.StateArguments;

import java.util.*;

public class ApplyFiltersArgs implements StateArguments {
    public ArrayList<Filter.Types> filter_types = new ArrayList<>();
    public Map<Filter.Types, Object> additional_filter_args = new HashMap<>();
}
