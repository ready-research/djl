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

package software.amazon.ai.integration.util;

import java.io.IOException;
import org.apache.mxnet.dataset.Mnist;
import org.apache.mxnet.dataset.SimpleDataset;
import software.amazon.ai.Block;
import software.amazon.ai.integration.exceptions.FailedTestException;
import software.amazon.ai.ndarray.NDArray;
import software.amazon.ai.ndarray.NDList;
import software.amazon.ai.ndarray.NDManager;
import software.amazon.ai.training.Gradient;
import software.amazon.ai.training.Loss;
import software.amazon.ai.training.Trainer;
import software.amazon.ai.training.TrainingController;
import software.amazon.ai.training.dataset.BatchSampler;
import software.amazon.ai.training.dataset.DataLoadingConfiguration;
import software.amazon.ai.training.dataset.Dataset;
import software.amazon.ai.training.dataset.RandomSampler;
import software.amazon.ai.training.dataset.Record;
import software.amazon.ai.training.metrics.Accuracy;
import software.amazon.ai.training.metrics.LossMetric;
import software.amazon.ai.training.optimizer.Optimizer;
import software.amazon.ai.training.optimizer.Sgd;
import software.amazon.ai.training.optimizer.learningrate.LrTracker;

public final class MnistUtils {

    private MnistUtils() {}

    public static void trainMnist(
            Block mlp, NDManager manager, int numEpoch, float expectedLoss, float expectedAccuracy)
            throws FailedTestException, IOException {
        // TODO remove numpy flag

        int batchSize = 100;

        Optimizer optimizer =
                new Sgd.Builder()
                        .setRescaleGrad(1.0f / batchSize)
                        .setLrTracker(LrTracker.fixedLR(0.1f))
                        .optMomentum(0.9f)
                        .build();

        TrainingController controller = new TrainingController(mlp.getParameters(), optimizer);
        Accuracy acc = new Accuracy();
        LossMetric lossMetric = new LossMetric("softmaxCELoss");

        SimpleDataset mnist =
                new Mnist(
                        manager,
                        Dataset.Usage.TRAIN,
                        new BatchSampler(new RandomSampler(), batchSize, true),
                        new DataLoadingConfiguration.Builder().build());
        mnist.prepare();
        try (Trainer<NDArray, NDArray, NDArray> trainer =
                Trainer.newInstance(mlp, new SimpleDataset.DefaultTranslator())) {
            for (int epoch = 0; epoch < numEpoch; epoch++) {
                // reset loss and accuracy
                acc.reset();
                lossMetric.reset();
                NDArray loss;
                for (Record record : trainer.trainDataset(mnist)) {
                    NDArray data = record.getData().head().reshape(batchSize, 28 * 28).div(255f);
                    NDArray label = record.getLabels().head();
                    NDArray pred;
                    try (Gradient.Collector gradCol = Gradient.newCollector()) {
                        pred = mlp.forward(new NDList(data)).head();
                        loss = Loss.softmaxCrossEntropyLoss(label, pred, 1.f, 0, -1, true, false);
                        gradCol.backward(loss);
                    }
                    controller.step();
                    acc.update(label, pred);
                    lossMetric.update(loss);
                }
            }
        }
        // final loss is sum of all loss divided by num of data
        float lossValue = lossMetric.getMetric().getValue();
        float accuracy = acc.getMetric().getValue();
        Assertions.assertTrue(
                lossValue <= expectedLoss,
                String.format(
                        "Loss did not improve, loss value: %f, expected "
                                + "maximal loss value: %f",
                        lossValue, expectedLoss));
        Assertions.assertTrue(
                accuracy >= expectedAccuracy,
                String.format(
                        "Accuracy did not improve, accuracy value: %f, expected "
                                + "minimal accuracy value: %f",
                        accuracy, expectedAccuracy));
    }
}
