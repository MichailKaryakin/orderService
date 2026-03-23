CREATE TABLE orders
(
    id           UUID                     NOT NULL,
    order_number VARCHAR(255)             NOT NULL,
    user_id      UUID                     NOT NULL,
    total_amount DECIMAL(19, 2)           NOT NULL,
    currency     VARCHAR(3)               NOT NULL,
    status       VARCHAR(50)              NOT NULL,

    street       VARCHAR(255),
    city         VARCHAR(255),
    state        VARCHAR(255),
    zip_code     VARCHAR(50),
    country      VARCHAR(255),

    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uq_orders_order_number UNIQUE (order_number)
);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_status ON orders (status);