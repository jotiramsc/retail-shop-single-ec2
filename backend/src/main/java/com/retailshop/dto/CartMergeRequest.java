package com.retailshop.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CartMergeRequest {
    private List<CartItemRequest> items = new ArrayList<>();
}
