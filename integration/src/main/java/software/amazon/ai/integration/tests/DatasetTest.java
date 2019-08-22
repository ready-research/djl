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
package software.amazon.ai.integration.tests;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import software.amazon.ai.Block;
import software.amazon.ai.Context;
import software.amazon.ai.integration.IntegrationTest;
import software.amazon.ai.integration.exceptions.FailedTestException;
import software.amazon.ai.integration.util.Assertions;
import software.amazon.ai.integration.util.RunAsTest;
import software.amazon.ai.ndarray.NDArray;
import software.amazon.ai.ndarray.NDList;
import software.amazon.ai.ndarray.NDManager;
import software.amazon.ai.ndarray.types.DataType;
import software.amazon.ai.training.Trainer;
import software.amazon.ai.training.dataset.ArrayDataset;
import software.amazon.ai.training.dataset.BatchSampler;
import software.amazon.ai.training.dataset.DataLoadingConfiguration;
import software.amazon.ai.training.dataset.RandomSampler;
import software.amazon.ai.training.dataset.Record;
import software.amazon.ai.training.dataset.SequenceSampler;

public class DatasetTest {

    public static void main(String[] args) {
        String[] cmd = {"-c", DatasetTest.class.getName()};
        new IntegrationTest()
                .runTests(
                        Stream.concat(Arrays.stream(cmd), Arrays.stream(args))
                                .toArray(String[]::new));
    }

    @RunAsTest
    public void testSequenceSampler() throws FailedTestException, IOException {
        try (NDManager manager = NDManager.newBaseManager()) {
            ArrayDataset dataset =
                    new ArrayDataset(
                            manager.arange(0, 100, 1, DataType.INT64, Context.defaultContext()),
                            new BatchSampler(new SequenceSampler(), 1, false),
                            new DataLoadingConfiguration.Builder().build());
            List<Long> original = new ArrayList<>();
            try (Trainer<NDList, NDList, NDList> trainer =
                    Trainer.newInstance(
                            Block.IDENTITY_BLOCK, new ArrayDataset.DefaultTranslator())) {
                trainer.trainDataset(dataset)
                        .iterator()
                        .forEachRemaining(
                                record -> original.add(record.getData().get(0).getLong()));
                List<Long> expected = LongStream.range(0, 100).boxed().collect(Collectors.toList());
                Assertions.assertTrue(original.equals(expected), "SequentialSampler test failed");
            }
        }
    }

    @RunAsTest
    public void testRandomSampler() throws FailedTestException, IOException {
        try (NDManager manager = NDManager.newBaseManager()) {
            ArrayDataset dataset =
                    new ArrayDataset(
                            manager.arange(0, 10, 1, DataType.INT64, Context.defaultContext()),
                            new BatchSampler(new RandomSampler(), 1, false),
                            new DataLoadingConfiguration.Builder().build());
            List<Long> original = new ArrayList<>();
            try (Trainer<NDList, NDList, NDList> trainer =
                    Trainer.newInstance(
                            Block.IDENTITY_BLOCK, new ArrayDataset.DefaultTranslator())) {
                trainer.trainDataset(dataset)
                        .iterator()
                        .forEachRemaining(
                                record -> original.add(record.getData().get(0).getLong()));
                Assertions.assertTrue(original.size() == 10, "SequentialSampler test failed");
            }
        }
    }

    @RunAsTest
    public void testBatchSampler() throws FailedTestException, IOException {
        try (NDManager manager = NDManager.newBaseManager()) {
            NDArray data = manager.arange(0, 100, 1, DataType.INT64, Context.defaultContext());

            ArrayDataset dataset =
                    new ArrayDataset(
                            data,
                            new BatchSampler(new SequenceSampler(), 27, false),
                            new DataLoadingConfiguration.Builder().build());
            List<long[]> originalList = new ArrayList<>();
            try (Trainer<NDList, NDList, NDList> trainer =
                    Trainer.newInstance(
                            Block.IDENTITY_BLOCK, new ArrayDataset.DefaultTranslator())) {
                trainer.trainDataset(dataset)
                        .iterator()
                        .forEachRemaining(
                                record -> originalList.add(record.getData().get(0).toLongArray()));
                Assertions.assertTrue(
                        originalList.size() == 4, "size of BatchSampler is not correct");
                long[] expected = LongStream.range(0, 27).toArray();
                Assertions.assertTrue(
                        Arrays.equals(originalList.get(0), expected),
                        "data from BatchSampler is not correct");
            }

            ArrayDataset dataset2 =
                    new ArrayDataset(
                            data,
                            new BatchSampler(new RandomSampler(), 33, true),
                            new DataLoadingConfiguration.Builder().build());
            List<long[]> originalList2 = new ArrayList<>();
            try (Trainer<NDList, NDList, NDList> trainer =
                    Trainer.newInstance(
                            Block.IDENTITY_BLOCK, new ArrayDataset.DefaultTranslator())) {
                trainer.trainDataset(dataset2)
                        .iterator()
                        .forEachRemaining(
                                record -> originalList2.add(record.getData().get(0).toLongArray()));
                Assertions.assertTrue(
                        originalList2.size() == 3, "size of BatchSampler is not correct");
            }

            // test case when dataset is smaller than batchSize, dropLast=true
            ArrayDataset dataset3 =
                    new ArrayDataset(
                            data,
                            new BatchSampler(new SequenceSampler(), 101, true),
                            new DataLoadingConfiguration.Builder().build());
            List<long[]> originalList3 = new ArrayList<>();
            try (Trainer<NDList, NDList, NDList> trainer =
                    Trainer.newInstance(
                            Block.IDENTITY_BLOCK, new ArrayDataset.DefaultTranslator())) {
                trainer.trainDataset(dataset3)
                        .iterator()
                        .forEachRemaining(
                                record -> originalList3.add(record.getData().get(0).toLongArray()));
                Assertions.assertTrue(
                        originalList3.isEmpty(), "size of BatchSampler is not correct");
            }

            // test case when dataset is smaller than batchSize, dropLast=false
            ArrayDataset dataset4 =
                    new ArrayDataset(
                            data,
                            new BatchSampler(new SequenceSampler(), 101, false),
                            new DataLoadingConfiguration.Builder().build());
            List<long[]> originalList4 = new ArrayList<>();
            try (Trainer<NDList, NDList, NDList> trainer =
                    Trainer.newInstance(
                            Block.IDENTITY_BLOCK, new ArrayDataset.DefaultTranslator())) {
                trainer.trainDataset(dataset4)
                        .iterator()
                        .forEachRemaining(
                                record -> originalList4.add(record.getData().get(0).toLongArray()));
                Assertions.assertTrue(
                        originalList4.size() == 1 && originalList4.get(0).length == 100,
                        "size of BatchSampler is not correct");
            }
        }
    }

    @RunAsTest
    public void testArrayDataset() throws FailedTestException, IOException {
        try (NDManager manager = NDManager.newBaseManager()) {
            NDArray data = manager.arange(200).reshape(100, 2);
            NDArray label = manager.arange(100).reshape(100);
            ArrayDataset dataset =
                    new ArrayDataset(
                            data,
                            label,
                            new BatchSampler(new SequenceSampler(), 20),
                            new DataLoadingConfiguration.Builder().build());
            int index = 0;
            try (Trainer<NDList, NDList, NDList> trainer =
                    Trainer.newInstance(
                            Block.IDENTITY_BLOCK, new ArrayDataset.DefaultTranslator())) {
                for (Record record : trainer.trainDataset(dataset)) {
                    Assertions.assertEquals(
                            record.getData().get(0),
                            manager.arange(2 * index, 2 * index + 40).reshape(20, 2));
                    Assertions.assertEquals(
                            record.getLabels().get(0),
                            manager.arange(index, index + 20).reshape(20));
                    index += 20;
                }

                dataset =
                        new ArrayDataset(
                                data,
                                label,
                                new BatchSampler(new SequenceSampler(), 15),
                                new DataLoadingConfiguration.Builder().build());
                index = 0;
                for (Record record : trainer.trainDataset(dataset)) {
                    if (index != 90) {
                        Assertions.assertEquals(
                                record.getData().get(0),
                                manager.arange(2 * index, 2 * index + 30).reshape(15, 2));
                        Assertions.assertEquals(
                                record.getLabels().get(0),
                                manager.arange(index, index + 15).reshape(15));
                    } else {
                        // last batch
                        Assertions.assertEquals(
                                record.getData().get(0),
                                manager.arange(2 * index, 2 * index + 20).reshape(10, 2));
                        Assertions.assertEquals(
                                record.getLabels().get(0),
                                manager.arange(index, index + 10).reshape(10));
                    }
                    index += 15;
                }
            }
        }
    }
}
