-- Crossing x direction x lane x local hour over the last recent_days days: the chart's grain.
--   n           readings with an estimate (closed, pending and n/a readings have none)
--   days        local dates with any reading in that hour, closed ones included
--   valid_days  local dates with at least one estimate in that hour: what the chart's gates use
select
    crossing_id,
    direction,
    lane,
    hour(timezone(iana_zone, reading_at)) as local_hour,
    count(delay_min) as n,
    count(distinct cast(timezone(iana_zone, reading_at) as date)) as days,
    count(distinct cast(timezone(iana_zone, reading_at) as date)) filter (where delay_min is not null) as valid_days,
    median(delay_min) as median_delay_min,
    quantile_cont(delay_min, 0.9) as p90_delay_min
from "bwt"."main"."fct_wait_readings_recent"
group by all
order by all