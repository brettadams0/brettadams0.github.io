-- The same with local weekday added, over ALL stored history rather than the
-- last recent_days days: a weekday x hour cell fills seven times more slowly.
-- For the SQL console only: weekday medians can't be combined into an all-days
-- median, so the chart never uses this.
select
    crossing_id,
    direction,
    lane,
    dayofweek(timezone(iana_zone, reading_at)) as local_weekday,
    hour(timezone(iana_zone, reading_at)) as local_hour,
    count(delay_min) as n,
    count(distinct cast(timezone(iana_zone, reading_at) as date)) as days,
    count(distinct cast(timezone(iana_zone, reading_at) as date)) filter (where delay_min is not null) as valid_days,
    median(delay_min) as median_delay_min,
    quantile_cont(delay_min, 0.9) as p90_delay_min
from "bwt"."main"."fct_wait_readings"
group by all
order by all