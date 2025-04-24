package org.ServerSide.ClientRequests;

import org.Domain.Client;
import org.Domain.Shop;
import org.Filters.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

public class FilterReader {
    public static ArrayList<Filter> readFilters(ObjectInputStream in, Client client) throws IOException, ClassNotFoundException {
        ArrayList<Filter> filters = new ArrayList<>();

        int ord_filter = in.readInt();
        Filter.Types specific_filters = Filter.Types.values()[ord_filter];

        while (specific_filters != Filter.Types.END) {
            System.out.println("Received filter " + specific_filters.toString() + " with arguments: ");
            Filter filter = switch (specific_filters) {
                case FILTER_STARS -> {
                    float min_rating = in.readFloat();
                    System.out.println("Float: " + min_rating);
                    yield new RateFilter<Shop>(min_rating);
                }
                case FILTER_PRICE -> {
                    int ord_pr_cat = in.readInt();
                    PriceCategoryEnum pr_cat = PriceCategoryEnum.values()[ord_pr_cat];
                    System.out.println("PriceCategory: " + pr_cat.toString());
                    yield new PriceCategoryFilter<Shop>(pr_cat);
                }
                case FILTER_CATEGORY -> {
                    @SuppressWarnings("unchecked") Set<String> shop_categories = (TreeSet<String>) in.readObject();
                    System.out.println("Set<String> categories: [ ");
                    shop_categories.forEach(System.out::println);
                    System.out.println("]");

                    yield new SameCategory<Shop>(shop_categories);
                }
                case FILTER_RADIUS -> {
                    double max_radius = in.readDouble();
                    System.out.println("max_radius = " + max_radius);

                    yield new InRangeFilter<Shop>(client.getLocation(), max_radius);
                }
                default -> throw new IllegalStateException("Unexpected value: " + specific_filters);
            };
            filters.add(filter);

            ord_filter = in.readInt();
            specific_filters = Filter.Types.values()[ord_filter];
        }

        return filters;
    }
}
