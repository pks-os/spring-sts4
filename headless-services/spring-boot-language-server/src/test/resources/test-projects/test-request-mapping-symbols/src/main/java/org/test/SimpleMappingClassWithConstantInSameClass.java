package org.test;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SimpleMappingClassWithConstantInSameClass {
	
	private static final String REQUEST_MAPPING_PATH_IN_SAME_CLASS = "/request/mapping/path/from/same/class/constant";

	@RequestMapping(REQUEST_MAPPING_PATH_IN_SAME_CLASS)
	public String hello() {
		return "Hello";
	}

}
