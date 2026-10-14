import Foundation

@main
enum FeedDetailParserTests {
    static func main() throws {
        let direct = try FeedDetailParser.parse(
            #"{"data":{"id":42,"entityType":"feed","username":"酷友","message":"完整正文"}}"#,
            feedID: "42"
        )
        precondition(direct.id == "42" && direct.message == "完整正文")

        let nested = try FeedDetailParser.parse(
            #"{"data":{"detail":{"id":"86","entityType":"feed","message":"嵌套正文"}}}"#,
            feedID: "86"
        )
        precondition(nested.id == "86" && nested.message == "嵌套正文")

        do {
            _ = try FeedDetailParser.parse(
                #"{"data":{"id":"86","entityType":"feed","message":"其他动态"}}"#,
                feedID: "42"
            )
            preconditionFailure("不应显示错误动态")
        } catch FeedDetailParseError.missingFeed {
            print("FeedDetailParserTests passed")
        }
    }
}
