/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance
 * with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package software.amazon.ai.training.dataset;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.stream.LongStream;
import software.amazon.ai.training.Trainer;
import software.amazon.ai.util.RandomUtils;

public class RandomSampler implements Sampler.SubSampler {

    private static void swap(long[] arr, int i, int j) {
        long tmp = arr[i];
        arr[i] = arr[j];
        arr[j] = tmp;
    }

    @Override
    public Iterator<Long> sample(Trainer<?, ?, ?> trainer, RandomAccessDataset<?, ?> dataset) {
        return new Iterate(trainer, dataset);
    }

    static class Iterate implements Iterator<Long> {

        private long[] indices;
        private long current;

        Iterate(Trainer<?, ?, ?> trainer, RandomAccessDataset<?, ?> dataset) {
            long size = dataset.size();
            current = 0;
            indices = LongStream.range(0, size).toArray();
            Random rnd = trainer.getSeed().map(Random::new).orElse(RandomUtils.RANDOM);
            // java array didn't support index greater than max integer
            // so cast to int for now
            for (int i = Math.toIntExact(size) - 1; i > 0; --i) {
                swap(indices, i, rnd.nextInt(i));
            }
        }

        @Override
        public boolean hasNext() {
            return current < indices.length;
        }

        @Override
        public Long next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            // java array didn't support index greater than max integer
            // so cast to int for now
            return indices[Math.toIntExact(current++)];
        }
    }
}
