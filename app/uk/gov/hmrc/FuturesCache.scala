/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc

import _root_.play.{api => playapi}
import playapi.Logger

import java.util.concurrent.{Callable, ExecutorService, Executors, TimeUnit}

import com.google.common.cache.{CacheBuilder, CacheLoader}
import com.google.common.util.concurrent.{ListenableFuture, ListenableFutureTask}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

trait FuturesCache[K, V] {
  private val executor: ExecutorService = Executors.newCachedThreadPool()

  def refreshTimeInMillis: Duration

  protected def cacheLoader: K => Future[V]

  lazy val cache = {

    CacheBuilder
      .newBuilder()
      .refreshAfterWrite(refreshTimeInMillis.toMillis, TimeUnit.MILLISECONDS)
      .build(
        new CacheLoader[K, Future[V]] {
          override def load(key: K): Future[V] = cacheLoader(key)

          override def reload(key: K, oldValue: Future[V]): ListenableFuture[Future[V]] = {
            val p = Promise[V]()

            val task = ListenableFutureTask.create(new Callable[Future[V]]() {
              def call(): Future[V] = {
                val loadF: Future[V] = cacheLoader(key)
                loadF.onComplete {
                  case Success(v) => p.success(v)
                  case Failure(t) =>
                    Logger.warn(s"Error while loading cache for Key :$key retaining the old value", t)
                    p.completeWith(oldValue)
                }

                p.future
              }
            })
            executor.execute(task)
            task
          }
        }
      )
  }

}
