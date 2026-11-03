package com.eatsnow.restaurants.domain.model;

/** Local restaurant inbox states (Spec 2.1). REJECTED and PREPARING are final for this service. */
public enum RestaurantOrderStatus {
    PENDING, ACCEPTED, REJECTED, PREPARING
}
