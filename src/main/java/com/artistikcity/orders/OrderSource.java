package com.artistikcity.orders;

import java.util.List;

/** One order system (courses, marketplace cart, older marketplace checkout, commissions) seen as unified orders. */
public interface OrderSource {

    /** Every order of this user in this system, any order (the service sorts). Never null. */
    List<UnifiedOrder> forUser(long userId);
}
