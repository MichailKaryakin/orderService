CREATE TABLE order_items
(
    id           UUID           NOT NULL,
    order_id     UUID           NOT NULL,
    product_id   UUID           NOT NULL,
    sku          VARCHAR(255)   NOT NULL,
    product_name VARCHAR(255)   NOT NULL,
    quantity     INT            NOT NULL,
    price        DECIMAL(19, 2) NOT NULL,

    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);