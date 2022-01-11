//------------------------------------------------------------------------------
// <auto-generated>
//     This code was generated from a template.
//
//     Manual changes to this file may cause unexpected behavior in your application.
//     Manual changes to this file will be overwritten if the code is regenerated.
// </auto-generated>
//------------------------------------------------------------------------------

namespace Sequencing.WeatherApp.Models
{
    using System;
    using System.Collections.Generic;
    
    public partial class Recommendation
    {
        public string Forecast { get; set; }
        public long CondId { get; set; }
        public long VitaminDId { get; set; }
        public long MelanomaRiskId { get; set; }
        public string Language { get; set; }
        public long GroupItemId { get; set; }
        public int AppTypeId { get; set; }
    
        public virtual Condition Condition { get; set; }
        public virtual MelanomaRisk MelanomaRisk { get; set; }
        public virtual VitaminD VitaminD { get; set; }
        public virtual ApplicationName ApplicationName { get; set; }
    }
}
