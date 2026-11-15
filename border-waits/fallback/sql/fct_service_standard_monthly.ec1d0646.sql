-- CBSA history with month_start: April to December belong to the fiscal year's
-- first calendar year, January to March to its second (2019-20 January is 2020-01).
select
    c.crossing_id,
    h.port_of_entry,
    h.fiscal_year,
    make_date(
        cast(left(h.fiscal_year, 4) as integer)
            + case when m.month_num <= 3 then 1 else 0 end,
        m.month_num, 1) as month_start,
    h.pct_met
from "bwt"."main"."stg_cbsa_land_monthly" h
join (
    select * from (values
        ('January', 1), ('February', 2), ('March', 3), ('April', 4), ('May', 5), ('June', 6),
        ('July', 7), ('August', 8), ('September', 9), ('October', 10), ('November', 11), ('December', 12)
    ) as t(month_name, month_num)
) m using (month_name)
left join "bwt"."main"."dim_crossing" c on c.cbsa_history_name = h.port_of_entry
order by h.port_of_entry, month_start