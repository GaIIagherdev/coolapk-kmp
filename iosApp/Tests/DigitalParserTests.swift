import Foundation

@main
enum DigitalParserTests {
    static func main() {
        let config = DigitalParser.config(
            #"""
            {"code":200,"data":{"selectedHomeTab":"V10_DIGITAL_HOME","configPages":[
                {"entityType":"ConfigPage","title":"数码首页","pageName":"V10_DIGITAL_HOME","url":"V10_DIGITAL_HOME","entities":[{"title":"手机","pageName":"V10_PHONE"}]},
                {"entityType":"ConfigPage","title":"品牌","pageName":"V10_PRODUCT_LIST","url":"/product/categoryList","order":1}
            ]}}
            """#
        )
        precondition(config.tabs.map(\.title) == ["数码首页", "品牌"])
        precondition(config.selectedKey == "V10_DIGITAL_HOME")
        precondition(config.tabs.first?.rawEntities.first?.title == "手机")
        precondition(config.tabs.last?.category == true)

        let page = DigitalParser.page(
            #"""
            {"code":200,"data":[
                {"entityTemplate":"imageCarouselCard","entities":[{"id":"banner-1","title":"新品","pic":"//image.coolapk.com/banner.png","url":"https://example.com"}]},
                {"entityTemplate":"iconLinkGridCard","title":"热门分类","entities":[{"id":"cat-1","title":"手机","icon":"phone.png"},{"id":"cat-2","title":"平板","icon":"tablet.png"}]},
                {"entityTemplate":"product","productId":"p-1","title":"旗舰手机","price_min":3999,"price_max":4999,"product_specs":"骁龙|OLED"}
            ],"firstItem":"a","lastItem":"b","hasMore":false}
            """#
        )
        precondition(page.items.count == 3)
        precondition(page.items[0].entities.first?.imageURL?.absoluteString == "https://image.coolapk.com/banner.png")
        precondition(page.items[1].entities.count == 2)
        precondition(page.items[2].productID == "p-1")
        precondition(page.items[2].price == "¥3999-4999")
        precondition(page.items[2].productSpecs == ["骁龙", "OLED"])
        precondition(page.firstItem == "a" && page.lastItem == "b" && !page.hasMore)

        let mixedConfig = DigitalParser.config(
            #"""
            {"data":{"extraData":"{\"selectedHomeTab\":\"V10_DIGITAL_HOME\"}","configPages":[
                {"entityType":"ConfigPage","title":"头条","pageName":"V9_HOME_TAB_HEADLINE"},
                {"entityType":"ConfigPage","title":"隐藏","pageName":"V10_DIGITAL_HIDDEN","visibility":"hidden"},
                {"entityType":"ConfigPage","title":"数码首页","pageName":"V10_DIGITAL_HOME","order":2,
                 "requestParams":{"type":"phone"},"entities":"[{\"id\":\"phone\",\"title\":\"手机\"}]"},
                {"entityType":"ConfigPage","title":"数码库","pageName":"V10_PRODUCT_LIST","url":"/product/categoryList","order":1}
            ]}}
            """#
        )
        precondition(mixedConfig.tabs.map(\.title) == ["数码库", "数码首页"])
        precondition(mixedConfig.selectedKey == "V10_DIGITAL_HOME")
        precondition(mixedConfig.tabs.last?.rawEntities.first?.title == "手机")
        precondition(mixedConfig.tabs.last?.requestArgsJSON == #"{"type":"phone"}"#)

        let contextualPage = DigitalParser.page(
            #"""
            {"data":[
                {"entityId":"config","entityTemplate":"configCard","extraData":{"pageContext":"next"}},
                {"entityId":"outer","entities":[
                    {"entityId":"banner","entityTemplate":"imageCarouselCard_1","entities":[{"id":"p1","title":"新品"}]},
                    {"id":"sponsored","entityTemplate":"sponsorCard"}
                ]}
            ],"hasMore":true}
            """#
        )
        precondition(contextualPage.items.map(\.id) == ["banner"])
        precondition(contextualPage.items.first?.entities.first?.id == "p1")
        precondition(contextualPage.firstItem == "config" && contextualPage.lastItem == "outer")
        precondition(contextualPage.pageContext == "next" && contextualPage.hasMore)

        let brandPage = DigitalParser.page(#"{"data":[{"id":91,"entityType":"brand","type":"recommend","title":"品牌"}]}"#)
        precondition(brandPage.items.first?.id == "91")
        precondition(brandPage.items.first?.brandType == "recommend")
        print("DigitalParserTests passed")
    }
}
