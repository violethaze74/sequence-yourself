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
    
    public partial class DeviceInfo
    {
        public long id { get; set; }
        public Nullable<long> userId { get; set; }
        public Nullable<DeviceType> deviceType { get; set; }
        public string token { get; set; }
        public Nullable<System.DateTime> subscriptionDate { get; set; }
        public Nullable<ApplicationType> applicationId { get; set; }
        public string appVersion { get; set; }
    }
}
