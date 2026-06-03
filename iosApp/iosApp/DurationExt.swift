
extension Duration {
    static func days(_ value: Int) -> Duration {
        return .seconds(value * 86400)
    }
    
    /// Returns the total number of half-nanoseconds in the duration.
    var halfNanoseconds: Int64 {
        let (seconds, attoseconds) = self.components
        
        // 1 second = 2,000,000,000 half-nanoseconds
        let halfSecondsFromSeconds = seconds * 2_000_000_000
        
        // 1 half-nanosecond = 500,000,000 attoseconds
        let halfSecondsFromAttoseconds = attoseconds / 500_000_000
        
        return halfSecondsFromSeconds + halfSecondsFromAttoseconds
    }
}

