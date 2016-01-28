package com.avast.metrics.core.multi;

import com.avast.metrics.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Wrapper around multiple monitors that adds possibility of per instance and summary metrics stored in a single object
 * and updated by a single call instead of two separate ones.
 * <p>
 * The first of the wrapped monitors is the main monitor instance, it should be the most concrete one. The second
 * is typically a summary per all instances. Only two wrapped monitors are expected but whatever number of monitors is
 * supported to make the code more generic.
 *
 * <h3>Limitations</h3>
 * <p>
 * The updates are applied to all wrapped monitors the same way, but queries for names and values will always operate only
 * on the first one which represents the monitored instance. Note it is impossible to return two ints at place of one int,
 * adding them together would typically produce a value that doesn't make any sense at all.
 * <p>
 * {@link #remove(Metric)} removes the metric only from the first wrapped monitor. The summary monitor is shared by
 * multiple instances so the remove might cause some unexpected problems in such case.
 * <p>
 * {@link #newGauge(String, Supplier)} is also registered only to the first wrapped monitor. There would be JMX conflicts
 * with the summary monitor shared by multiple instances.
 *
 * <h3>Typical usage</h3>
 *
 * <pre><code>
 *     Monitor summaryMonitor = monitor.named("path");
 *     Monitor instanceMonitor = summaryMonitor.named("instance");
 *
 *     Monitor monitor = MultiMonitor.of(instanceMonitor, summaryMonitor);
 *
 *     this.errors = monitor.newMeter("errors");
 *     this.requests = monitor.newMeter("requests");
 * </code></pre>
 */
public class MultiMonitor implements Monitor {
    private final List<Monitor> monitors;

    /**
     * Factory method. Read {@link MultiMonitor} limitations!
     *
     * @param instanceMonitor non-shared main monitor for data from a single instance
     * @param summaryMonitor  shared summary monitor counting sums per all instances
     * @param otherMonitors   optional other monitors
     * @return multi monitor containing all passed monitors
     */
    public static Monitor of(Monitor instanceMonitor, Monitor summaryMonitor, Monitor... otherMonitors) {
        List<Monitor> allMonitors = new ArrayList<>();
        allMonitors.add(instanceMonitor);
        allMonitors.add(summaryMonitor);
        allMonitors.addAll(Arrays.asList(otherMonitors));

        return new MultiMonitor(allMonitors);
    }

    private MultiMonitor(List<Monitor> monitors) {
        if (monitors.size() < 2) {
            throw new IllegalArgumentException("Multi monitor from less than 2 monitors makes no sense");
        }

        this.monitors = monitors;
    }

    @Override
    public Monitor named(String name) {
        return monitors.get(0).named(name);
    }

    @Override
    public Monitor named(String name1, String name2, String... restOfNames) {
        return monitors.get(0).named(name1, name2, restOfNames);
    }

    @Override
    public String getName() {
        return monitors.get(0).getName();
    }

    @Override
    public Meter newMeter(String name) {
        List<Meter> meters = monitors.stream()
                .map(m -> m.newMeter(name))
                .collect(Collectors.toList());

        return new MultiMeter(meters);
    }

    @Override
    public Counter newCounter(String name) {
        List<Counter> counters = monitors.stream()
                .map(m -> m.newCounter(name))
                .collect(Collectors.toList());

        return new MultiCounter(counters);
    }

    @Override
    public Timer newTimer(String name) {
        List<Timer> timers = monitors.stream()
                .map(m -> m.newTimer(name))
                .collect(Collectors.toList());

        return new MultiTimer(timers);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Register the gauge only to the first wrapped monitor. There would be JMX conflicts with the "summary" monitor.
     *
     * @param name  gauge name
     * @param gauge method to compute the gauge value
     * @param <T>   type of computed value
     * @return new gauge
     */
    @Override
    public <T> Gauge<T> newGauge(String name, Supplier<T> gauge) {
        return monitors.get(0).newGauge(name, gauge);
    }

    @Override
    public Histogram newHistogram(String name) {
        List<Histogram> histograms = monitors.stream()
                .map(m -> m.newHistogram(name))
                .collect(Collectors.toList());

        return new MultiHistogram(histograms);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Remove the metric only from the first wrapped monitor. There would be JMX conflicts with the "summary" monitor
     * which is typically shared by multiple instances.
     *
     * @param metric metric to unregister
     */
    @Override
    public void remove(Metric metric) {
        monitors.get(0).remove(metric);
    }

    @Override
    public void close() {
        monitors.forEach(Monitor::close);
    }
}