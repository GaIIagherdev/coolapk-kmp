import Foundation

@main
enum DiscoveryParserTests {
    static func main() {
        let config = DiscoveryParser.config(
            #"""
            {"data":[
              {"id":1,"title":"首页","entities":[{"title":"不应出现","page_name":"V9_HOME"}]},
              {"id":20131,"title":"发现","extraDataArr":"{\"selectedHomeTab\":\"V11_FIND_LIFE\"}","entities":[
                {"entityType":"ConfigPage","title":"生活","page_name":"V11_FIND_LIFE","url":"V11_FIND_LIFE","order":2},
                {"entityType":"ConfigPage","title":"隐藏","url":"V11_FIND_HIDDEN","status":0},
                {"entityType":"ConfigPage","title":"酷图","url":"V11_FIND_COOLPIC","order":1,"tabIcon":"//image.coolapk.com/icon.png"},
                {"entityType":"ConfigPage","title":"酷图重复","url":"V11_FIND_COOLPIC"}
              ]}
            ]}
            """#
        )
        precondition(config.tabs.map(\.title) == ["酷图", "生活"])
        precondition(config.selectedKey == "V11_FIND_LIFE")
        precondition(config.tabs.first?.iconURL?.absoluteString == "https://image.coolapk.com/icon.png")
        precondition(DiscoveryParser.config(#"{"data":[]}"#).tabs.isEmpty)

        let page = DiscoveryParser.page(
            #"""
            {"data":[
              {"entityId":"config","entityType":"card","entityTemplate":"configCard","extraData":"{\"pageContext\":\"next-context\"}"},
              {"entityId":"card-1","entityType":"topic","title":"热点话题","pic":"topic.png"},
              {"id":"feed-1","entityId":"cursor-1","entityType":"feed","message":"动态内容","username":"酷友"}
            ],"firstItem":"start","lastItem":"end","hasMore":false}
            """#
        )
        precondition(page.cards.map(\.id) == ["card-1", "cursor-1"])
        precondition(page.cards.first?.imageURL?.absoluteString == "https://image.coolapk.com/topic.png")
        precondition(page.cards.last?.feedID == "feed-1")
        precondition(page.firstItem == "start" && page.lastItem == "end")
        precondition(page.pageContext == "next-context")
        precondition(!page.hasMore)

        let empty = DiscoveryParser.page(
            #"{"data":[{"entityType":"card","entityTemplate":"configCard","entityId":"only"},{"entityType":"card","entityTemplate":"sponsorCard","entityId":"ad"}]}"#
        )
        precondition(empty.cards.isEmpty && !empty.hasMore)

        let grouped = DiscoveryParser.page(
            """
            {"data":[{"entityId":"group-cursor","entityType":"card","entities":[
              {"entityId":"feed-1","entityType":"feed","message":"正文"},
              {"entityId":"topic-1","entityType":"topic","title":"话题"}
            ]}]}
            """
        )
        precondition(grouped.cards.map(\.id) == ["feed-1", "topic-1"])
        precondition(grouped.firstItem == "group-cursor" && grouped.lastItem == "group-cursor")
        precondition(grouped.cards.first?.feedID == "feed-1")

        let composed = DiscoveryParser.page(
            """
            {"data":[
              {"entityId":41629,"entityType":"card","entityTemplate":"imageCarouselCard_1",
               "title":"","url":"","pic":"http://image.coolapk.com/banner.jpg","entities":[
                 {"entityType":"image_1","pic":"http://image.coolapk.com/tile.jpg",
                  "title":"户外兴趣小组","url":"/t/户外兴趣小组"}
               ]},
              {"entityId":41630,"entityType":"card","entityTemplate":"selectorLinkCard",
               "title":"","url":"","entities":[
                 {"entityType":"selectorLink","title":"热议","url":"#/feed/digestList?filterId=82"},
                 {"entityType":"selectorLink","title":"兴趣爱好","url":"#/feed/digestList?filterId=78"}
               ]}
            ]}
            """
        )
        precondition(composed.cards.map(\.title) == ["户外兴趣小组", "热议", "兴趣爱好"])
        precondition(composed.cards.first?.imageURL?.absoluteString == "https://image.coolapk.com/banner.jpg")
        precondition(composed.cards.first?.url == "/t/户外兴趣小组")
        precondition(composed.cards.last?.url == "#/feed/digestList?filterId=78")
        precondition(composed.firstItem == "41629" && composed.lastItem == "41630")

        let imageFallback = DiscoveryParser.page(
            #"{"data":[{"entityId":"image","entityType":"topic","pic":"","logo":"logo.png"}]}"#
        )
        precondition(imageFallback.cards.first?.imageURL?.absoluteString == "https://image.coolapk.com/logo.png")
        print("DiscoveryParserTests passed")
    }
}
