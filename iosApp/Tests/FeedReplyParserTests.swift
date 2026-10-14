import Foundation

@main
enum FeedReplyParserTests {
    static func main() {
        let page = FeedReplyParser.page("""
        {
          "data": {
            "list": [
              {
                "id": "101", "fid": "88", "username": "楼主", "message": "第一条",
                "dateline": 1720000000, "floor": "1", "likenum": "3",
                "replyRowsCount": 2, "userAvatar": "//image.coolapk.com/avatar.jpg",
                "picArr": [{"sourceUrl": "http://image.coolapk.com/reply.jpg"}],
                "replyRows": [{"username": "酷友", "message": "楼中楼"}]
              },
              {"id": "101", "message": "重复数据"},
              {"entityId": "102", "userInfo": {"username": "另一位"}, "message": "第二条"}
            ],
            "hasMore": false
          }
        }
        """)
        precondition(page.items.count == 2)
        precondition(!page.hasMore)
        precondition(page.lastItem == "102")
        precondition(page.items[0].username == "楼主")
        precondition(page.items[0].likes == 3)
        precondition(page.items[0].replyCount == 2)
        precondition(page.items[0].previews.first?.message == "楼中楼")
        precondition(page.items[0].imageURLs.first?.absoluteString == "https://image.coolapk.com/reply.jpg")
        precondition(page.items[1].username == "另一位")

        let empty = FeedReplyParser.page("{" + "\"data\":[],\"hasMore\":false}")
        precondition(empty.items.isEmpty && !empty.hasMore)
        print("FeedReplyParserTests passed")
    }
}
