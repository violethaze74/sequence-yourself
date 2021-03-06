
-- 1
-- 1. get list of non set items
-- 2. if that list of 0 size

-- searching for previously sent forecast for this day
select r.Forecast from [WeatherMyWay].[dbo].[SendForecast] sf
inner join [WeatherMyWay].[dbo].[Recommendations] r on (r.[GroupItemId] = sf.[SendForecastID])
where sf.[WeatherCond]=1 and sf.[VitaminD]=3 and sf.[MelanomaRisk]=1 and sf.[UserId] = 10084 --and sf.[ForecastDate] = GETDATE();

-- if there was no forecast made for specific day, then search through unsent items
select * from [WeatherMyWay].[dbo].[Recommendations]
where
[GroupItemId] not in
(select [SendForecastID] from [WeatherMyWay].[dbo].[SendForecast] where [WeatherCond]=1 and [VitaminD]=3 and [MelanomaRisk]=1) and
[CondId]=1 and [VitaminDId]=3 and [MelanomaRiskId]=1

-- if there are no unsent items then we should delete all previously sent items with specific melanomaid/vitdid/userid/condid
	-- take first forecast from [Recommendations] and send it to user

-- if there were items retrieved on 2nd step, pick a random one and return to user

-- store picked forecast into [SendForecast]


  select count(*), [CondId], [VitaminDId], [MelanomaRiskId] from [WeatherMyWay].[dbo].[Recommendations]
  group by [CondId], [VitaminDId], [MelanomaRiskId]
  having count(*) > 1

  select * from [WeatherMyWay].[dbo].[Recommendations]
  where [CondId]=1 and [VitaminDId]=3 and [MelanomaRiskId]=1