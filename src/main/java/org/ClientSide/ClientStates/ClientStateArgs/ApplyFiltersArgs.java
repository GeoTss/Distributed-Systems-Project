package org.ClientSide.ClientStates.ClientStateArgs;

import org.Filters.Filter;

import java.util.*;

public class ApplyFiltersArgs implements ClientStateArgument{
    public ArrayList<Filter.Types> filter_types = new ArrayList<>();
    public Map<Filter.Types, Object> additional_filter_args = new HashMap<>();
}
