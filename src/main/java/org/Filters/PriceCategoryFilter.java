package org.Filters;

public class PriceCategoryFilter<T extends PriceCategory> implements Filter {

    PriceCategoryEnum _pr_cat;

    public PriceCategoryFilter(PriceCategoryEnum _pr_cat) {
        this._pr_cat = _pr_cat;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean satisfy(Object obj) {
        return ((T) obj).getPriceCategory().getPriceVal() == _pr_cat.getPriceVal();
    }

}
