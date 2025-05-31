package com.example.client_efood.ClientSide.ClientStates.ClientStateArgs;

import com.example.client_efood.Filters.Filter;
import com.example.client_efood.StatePattern.StateArguments;

import java.util.*;

public class ApplyFiltersArgs implements StateArguments {
    public Set<Filter.Types> filter_types = new TreeSet<>();
    public Map<Filter.Types, Object> additional_filter_args = new HashMap<>();
}
