package io.eventuate.tram.consumer.redis;

import io.eventuate.tram.consumer.common.DecoratedMessageHandlerFactory;
import io.eventuate.tram.consumer.common.coordinator.CoordinatorFactory;
import io.eventuate.tram.consumer.common.coordinator.SubscriptionLeaderHook;
import io.eventuate.tram.consumer.common.coordinator.SubscriptionLifecycleHook;
import io.eventuate.tram.messaging.consumer.MessageConsumer;
import io.eventuate.tram.messaging.consumer.MessageHandler;
import io.eventuate.tram.messaging.consumer.MessageSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.*;
import java.util.function.Supplier;

public class MessageConsumerRedisImpl implements MessageConsumer {

  private Logger logger = LoggerFactory.getLogger(getClass());

  public final String consumerId;
  private Supplier<String> subscriptionIdSupplier;

  @Autowired
  private DecoratedMessageHandlerFactory decoratedMessageHandlerFactory;

  private RedisTemplate<String, String> redisTemplate;

  private List<Subscription> subscriptions = new ArrayList<>();
  private final CoordinatorFactory coordinatorFactory;
  private long timeInMillisecondsToSleepWhenKeyDoesNotExist;
  private long blockStreamTimeInMilliseconds;

  public MessageConsumerRedisImpl(RedisTemplate<String, String> redisTemplate,
                                  CoordinatorFactory coordinatorFactory,
                                  long timeInMillisecondsToSleepWhenKeyDoesNotExist,
                                  long blockStreamTimeInMilliseconds) {
    this(() -> UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            redisTemplate,
            coordinatorFactory,
            timeInMillisecondsToSleepWhenKeyDoesNotExist,
            blockStreamTimeInMilliseconds);
  }

  public MessageConsumerRedisImpl(Supplier<String> subscriptionIdSupplier,
                                  String consumerId,
                                  RedisTemplate<String, String> redisTemplate,
                                  CoordinatorFactory coordinatorFactory,
                                  long timeInMillisecondsToSleepWhenKeyDoesNotExist,
                                  long blockStreamTimeInMilliseconds) {

    this.subscriptionIdSupplier = subscriptionIdSupplier;
    this.consumerId = consumerId;
    this.redisTemplate = redisTemplate;
    this.coordinatorFactory = coordinatorFactory;
    this.timeInMillisecondsToSleepWhenKeyDoesNotExist = timeInMillisecondsToSleepWhenKeyDoesNotExist;
    this.blockStreamTimeInMilliseconds = blockStreamTimeInMilliseconds;

    logger.info("Consumer created (consumer id = {})", consumerId);
  }

  @Override
  public MessageSubscription subscribe(String subscriberId, Set<String> channels, MessageHandler handler) {

    logger.info("consumer subscribes to channels (consumer id = {}, subscriber id {}, channels = {})", consumerId, subscriberId, channels);

    Subscription subscription = new Subscription(subscriptionIdSupplier.get(),
            consumerId,
            redisTemplate,
            subscriberId,
            channels,
            decoratedMessageHandlerFactory.decorate(handler),
            coordinatorFactory,
            timeInMillisecondsToSleepWhenKeyDoesNotExist,
            blockStreamTimeInMilliseconds);

    subscriptions.add(subscription);

    return subscription::close;
  }

  public void setSubscriptionLifecycleHook(SubscriptionLifecycleHook subscriptionLifecycleHook) {
    subscriptions.forEach(subscription -> subscription.setSubscriptionLifecycleHook(subscriptionLifecycleHook));
  }

  public void setLeaderHook(SubscriptionLeaderHook leaderHook) {
    subscriptions.forEach(subscription -> subscription.setLeaderHook(leaderHook));
  }

  public void close() {
    subscriptions.forEach(Subscription::close);
    subscriptions.clear();
  }

  @Override
  public String getId() {
    return consumerId;
  }
}